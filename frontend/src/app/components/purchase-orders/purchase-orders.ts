import { ChangeDetectorRef, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { PurchaseOrderService } from '../../services/purchase-order/purchase-order-service';
import { Batch } from '../../models/batch.model';
import { PurchaseOrderLineDto, PurchaseOrderDto } from '../../models/odoo.model';
import { getExpiryStatus, daysUntilExpiry } from '../../models/ui-helpers.model'


@Component({
  selector: 'app-purchase-orders',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './purchase-orders.html',
  styleUrl: './purchase-orders.scss',
})
export class PurchaseOrders implements OnInit {
  orders      = signal<PurchaseOrderDto[]>([]);
  trackedIds  = signal<number[]>([]);
  loading     = signal(false);
  error       = signal<string | null>(null);
  filterDays  = signal(15);

  // Which PO card is expanded
  expandedOrderId = signal<number | null>(null);

  // Per-line form instances: lineId → FormGroup
  private formMap = new Map<number, FormGroup>();

  // Per-line edit state: lineId → { batchId } when editing, null when adding
  editingMap = new Map<number, number | null>();

  // Per-line saving/deleting spinners
  savingLineId    = signal<number | null>(null);
  deletingBatchId = signal<number | null>(null);

  readonly getExpiryStatus = getExpiryStatus;
  readonly daysUntilExpiry = daysUntilExpiry;

  constructor(
    private poService: PurchaseOrderService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadOrders();
  }

  // ── Load orders ───────────────────────────────────────────────────────────

  loadOrders(): void {
    this.loading.set(true);
    this.error.set(null);
    this.poService.list({
      days: this.filterDays(),
    }).subscribe({
      next: orders => {
        // Attach UI state to each line
        this.orders.set(orders.map(o => ({
          ...o,
          lines: o.lines.map(l => ({
            ...l,
            batches: [],
            loadingBatches: false,
            showBatchForm: false
          }))
        })));
        const loaded = this.orders();
        loaded.forEach(o =>
          o.lines.forEach(l => this.loadLineBatches(o, l))
        );
        this.cdr.detectChanges();
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.error ?? 'Failed to load purchase orders');
        this.loading.set(false);
      }
    });
  }

  setDays(days: number): void {
    this.filterDays.set(days);
    this.loadOrders();
  }

  // ── Expand / collapse PO card ─────────────────────────────────────────────

  toggleOrder(orderId: number): void {
    this.expandedOrderId.set(this.expandedOrderId() === orderId ? null : orderId);
  }

  isExpanded(id: number): boolean {
    return this.expandedOrderId() === id;
  }

  // ── Batch form per line ───────────────────────────────────────────────────

  getForm(lineId: number): FormGroup {
    if (!this.formMap.has(lineId)) {
      this.formMap.set(lineId, this.fb.group({
        batchNumber:  ['', Validators.required],
        expiryDate:   ['', Validators.required],
        deliveryDate: [''],
        quantity:     [null, [Validators.required, Validators.min(0.001)]],
        notes:        ['']
      }));
    }
    return this.formMap.get(lineId)!;
  }

  openAddForm(line: PurchaseOrderLineDto): void {
    line.showBatchForm = true;
    this.editingMap.set(line.id, null);
    this.getForm(line.id).reset({ quantity: null });
  }

  openEditForm(line: PurchaseOrderLineDto, batch: Batch): void {
    line.showBatchForm = true;
    this.editingMap.set(line.id, batch.id);
    this.getForm(line.id).patchValue({
      batchNumber:  batch.batchNumber,
      expiryDate:   batch.expiryDate,
      deliveryDate: batch.deliveryDate ?? '',
      quantity:     batch.quantity,
      notes:        batch.notes ?? ''
    });
  }

  closeForm(line: PurchaseOrderLineDto): void {
    line.showBatchForm = false;
    this.editingMap.delete(line.id);
    this.formMap.get(line.id)?.reset();
  }

  isEditing(lineId: number): boolean {
    return this.editingMap.has(lineId) && this.editingMap.get(lineId) !== null;
  }

  editingBatchId(lineId: number): number | null {
    return this.editingMap.get(lineId) ?? null;
  }

  // ── Load batches for a line (GET /{poName}/batches?odooProductId=) ─────────

  loadLineBatches(order: PurchaseOrderDto, line: PurchaseOrderLineDto): void {
    line.loadingBatches = true;
    this.poService.getBatchesForProduct(order.name, line.productId).subscribe({
      next: batches => {
        line.batches = batches;
        line.loadingBatches = false;
        this.orders.set([...this.orders()]);
        this.cdr.detectChanges();
      },
      error: () => { line.loadingBatches = false; this.orders.set([...this.orders()]); }
    });
  }

  // ── Save (add or edit) ────────────────────────────────────────────────────

  saveBatch(order: PurchaseOrderDto, line: PurchaseOrderLineDto): void {
    const form = this.getForm(line.id);
    if (form.invalid) { form.markAllAsTouched(); return; }

    this.savingLineId.set(line.id);
    const editingId = this.editingMap.get(line.id) ?? null;
    const val = form.value;

    if (editingId !== null) {
      // PATCH /{poName}/batches/{batchId}
      // Body: { odooProductId, fields: { ... } }
      this.poService.editBatch(order.name, editingId, {
        odooProductId: line.productId,
        fields: {
          batchNumber:  val.batchNumber,
          expiryDate:   val.expiryDate,
          deliveryDate: val.deliveryDate || null,
          quantity:     val.quantity,
          notes:        val.notes || null
        }
      }).subscribe({
        next: () => {
          this.savingLineId.set(null);
          this.closeForm(line);
          this.loadLineBatches(order, line);
          this.cdr.detectChanges();
        },
        error: err => {
          this.error.set(err.error?.error ?? 'Failed to update batch');
          this.savingLineId.set(null);
        }
      });
    } else {
      // POST /{poName}/batches
      // Body: AddBatchRequest — odooProductId + productName + batch fields
      this.poService.addBatch(order.name, {
        odooProductId: line.productId,
        productName:   line.productName,
        batchNumber:   val.batchNumber,
        expiryDate:    val.expiryDate,
        deliveryDate:  val.deliveryDate || undefined,
        quantity:      val.quantity,
        notes:         val.notes || undefined
      }).subscribe({
        next: () => {
          this.savingLineId.set(null);
          this.getForm(line.id).reset({ quantity: null });
          this.loadLineBatches(order, line);
          this.cdr.detectChanges();
        },
        error: err => {
          this.error.set(err.error?.error ?? 'Failed to add batch');
          this.savingLineId.set(null);
        }
      });
    }
  }

  // ── Delete batch (DELETE /{poName}/batches/{batchId} body: {odooProductId}) ─

  deleteBatch(order: PurchaseOrderDto, line: PurchaseOrderLineDto, batchId: number): void {
    if (!confirm('Delete this lot?')) return;
    this.deletingBatchId.set(batchId);
    this.poService.deleteBatch(order.name, batchId, { odooProductId: line.productId }).subscribe({
      next: () => {
        this.deletingBatchId.set(null);
        this.loadLineBatches(order, line);
      },
      error: () => this.deletingBatchId.set(null)
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  receiptProgress(line: PurchaseOrderLineDto): number {
    if (!line.orderedQty) return 0;
    return Math.min(100, Math.round((line.receivedQty / line.orderedQty) * 100));
  }

  hasError(lineId: number, field: string): boolean {
    const ctrl = this.getForm(lineId).get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }
}