import { ChangeDetectorRef, Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ProductService } from '../../services/product/product-service';
import { BatchService } from '../../services/batch/batch-service';
import { Product } from '../../models/product.model';
import { Batch } from '../../models/batch.model';
import { getExpiryStatus, daysUntilExpiry } from '../../models/ui-helpers.model';

@Component({
  selector: 'app-products',
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule
  ],
  templateUrl: './products.html',
  styleUrl: './products.scss',
})
export class Products implements OnInit {

  products        = signal<Product[]>([]);
  loading         = signal(true);
  showForm        = signal(false);
  saving          = signal(false);
  error           = signal<string | null>(null);
  searchQuery     = signal('');

  // Batch state
  expandedId        = signal<string | null>(null);
  batchesMap        = signal<Record<string, Batch[]>>({});
  loadingBatchesId  = signal<string | null>(null);
  savingProductId   = signal<string | null>(null);
  deletingBatchId   = signal<number | null>(null);

  private batchFormMap   = new Map<string, FormGroup>();
  private batchShowMap   = new Map<string, boolean>();
  editingBatchMap        = signal<Map<string, number | null>>(new Map());

  readonly getExpiryStatus = getExpiryStatus;
  readonly daysUntilExpiry = daysUntilExpiry;

  // Computed filtered list
  filtered = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    if (!q) return this.products();
    return this.products().filter(p =>
      p.name.toLowerCase().includes(q) ||
      (p.code ?? '').toLowerCase().includes(q) ||
      (p.category ?? '').toLowerCase().includes(q) ||
      (p.firestoreId  ?? '').toLowerCase().includes(q) ||        
      (p.odooId?.toString() ?? '').includes(q)  
    );
  });

  form: FormGroup;

  constructor(
    private productService: ProductService,
    private batchService: BatchService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      firestoreId: ['', Validators.required],
      name:        ['', Validators.required],
      odooId:      [null],
      code:        [''],
      category:    [''],
      source:      ['']
    });
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.productService.listAll().subscribe({
      next: p => { this.products.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    this.productService.upsert(this.form.value).subscribe({
      next: () => {
        this.showForm.set(false);
        this.form.reset();
        this.load();
        this.saving.set(false);
      },
      error: err => {
        this.error.set(err.error?.error ?? 'Save failed');
        this.saving.set(false);
      }
    });
  }

  // ── Expand / collapse ─────────────────────────────────────────────────────

  toggleProduct(id: string): void {
    if (this.expandedId() === id) {
      this.expandedId.set(null);
    } else {
      this.expandedId.set(id);
      if (!this.batchesMap()[id]) this.loadBatches(id);
    }
  }

  isExpanded(id: string): boolean { return this.expandedId() === id; }

  // ── Batch loading ─────────────────────────────────────────────────────────

  loadBatches(productId: string): void {
    this.loadingBatchesId.set(productId);
    this.batchService.listByProduct(productId).subscribe({
      next: batches => {
        this.batchesMap.set({ ...this.batchesMap(), [productId]: batches });
        this.loadingBatchesId.set(null);
        this.cdr.detectChanges();
      },
      error: () => this.loadingBatchesId.set(null)
    });
  }

  // ── Batch form ────────────────────────────────────────────────────────────

  getBatchForm(productId: string): FormGroup {
    if (!this.batchFormMap.has(productId)) {
      this.batchFormMap.set(productId, this.fb.group({
        batchNumber:  ['', Validators.required],
        expiryDate:   ['', Validators.required],
        deliveryDate: [''],
        quantity:     [null, [Validators.required, Validators.min(0.001)]],
        notes:        ['']
      }));
    }
    return this.batchFormMap.get(productId)!;
  }

  isBatchFormOpen(productId: string): boolean {
    return this.batchShowMap.get(productId) ?? false;
  }

  isEditingBatch(productId: string): boolean {
    return (this.editingBatchMap().get(productId) ?? null) !== null;
  }

  openAddBatchForm(productId: string): void {
    this.batchShowMap.set(productId, true);
    const m = new Map(this.editingBatchMap());
    m.set(productId, null);
    this.editingBatchMap.set(m);
    this.getBatchForm(productId).reset({ quantity: null });
  }

  openEditBatchForm(productId: string, batch: Batch): void {
    this.batchShowMap.set(productId, true);
    const m = new Map(this.editingBatchMap());
    m.set(productId, batch.id);
    this.editingBatchMap.set(m);
    this.getBatchForm(productId).patchValue({
      batchNumber:  batch.batchNumber,
      expiryDate:   batch.expiryDate,
      deliveryDate: batch.deliveryDate ?? '',
      quantity:     batch.quantity,
      notes:        batch.notes ?? ''
    });
  }

  closeBatchForm(productId: string): void {
    this.batchShowMap.set(productId, false);
    const m = new Map(this.editingBatchMap());
    m.delete(productId);
    this.editingBatchMap.set(m);
    this.getBatchForm(productId).reset();
  }

  batchHasError(productId: string, field: string): boolean {
    const ctrl = this.getBatchForm(productId).get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }

  // ── Save batch (add or edit) ──────────────────────────────────────────────

  saveBatch(productId: string): void {
    const form = this.getBatchForm(productId);
    if (form.invalid) { form.markAllAsTouched(); return; }

    this.savingProductId.set(productId);
    const val = form.value;
    const editingId = this.editingBatchMap().get(productId) ?? null;

    if (editingId !== null) {
      this.batchService.patch(productId, editingId, {
        batchNumber:  val.batchNumber,
        expiryDate:   val.expiryDate,
        deliveryDate: val.deliveryDate || null,
        quantity:     val.quantity,
        notes:        val.notes || null
      }).subscribe({
        next: () => {
          this.savingProductId.set(null);
          this.closeBatchForm(productId);
          this.loadBatches(productId);
        },
        error: err => {
          this.error.set(err.error?.error ?? 'Failed to update batch');
          this.savingProductId.set(null);
        }
      });
    } else {
      this.batchService.create(productId, {
        batchNumber:  val.batchNumber,
        expiryDate:   val.expiryDate,
        deliveryDate: val.deliveryDate || undefined,
        quantity:     val.quantity,
        notes:        val.notes || undefined
      }).subscribe({
        next: () => {
          this.savingProductId.set(null);
          this.closeBatchForm(productId);
          this.loadBatches(productId);
        },
        error: err => {
          this.error.set(err.error?.error ?? 'Failed to add batch');
          this.savingProductId.set(null);
        }
      });
    }
  }

  // ── Delete batch ──────────────────────────────────────────────────────────

  deleteBatch(productId: string, batchId: number): void {
    if (!confirm('Delete this lot?')) return;
    this.deletingBatchId.set(batchId);
    this.batchService.delete(productId, batchId).subscribe({
      next: () => {
        this.deletingBatchId.set(null);
        this.loadBatches(productId);
      },
      error: () => this.deletingBatchId.set(null)
    });
  }
}