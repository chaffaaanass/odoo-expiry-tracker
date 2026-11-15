import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BatchService } from '../../services/batch/batch-service';
import { Batch } from '../../models/batch.model';
import { daysUntilExpiry, getExpiryStatus } from '../../models/ui-helpers.model';

@Component({
  selector: 'app-batches',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './batches.html',
  styleUrl: './batches.scss',
})
export class Batches implements OnInit {
  productId = signal('');
  batches = signal<Batch[]>([]);
  loading = signal(true);
  showForm = signal(false);
  saving = signal(false);
  error = signal<string | null>(null);
  deletingId = signal<number | null>(null);

  readonly daysUntilExpiry = daysUntilExpiry;
  readonly getExpiryStatus = getExpiryStatus;

  form: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private batchService: BatchService,
    private fb: FormBuilder
  ) {
    this.form = this.fb.group({
      batchNumber:     ['', Validators.required],
      expiryDate:      ['', Validators.required],
      deliveryDate:    [''],
      quantity:        [0, [Validators.required, Validators.min(0)]],
      unit:            [''],
      purchaseOrderId: [''],
      notes:           ['']
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('productId') ?? '';
    this.productId.set(id);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.batchService.listByProduct(this.productId()).subscribe({
      next: b => { this.batches.set(b); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    this.batchService.create(this.productId(), this.form.value).subscribe({
      next: () => {
        this.form.reset({ quantity: 0 });
        this.showForm.set(false);
        this.load();
        this.saving.set(false);
      },
      error: err => {
        this.error.set(err.error?.error ?? 'Failed to save batch');
        this.saving.set(false);
      }
    });
  }

  deleteBatch(id: number): void {
    if (!confirm('Delete this batch?')) return;
    this.deletingId.set(id);
    this.batchService.delete(this.productId(), id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.load();
      },
      error: () => this.deletingId.set(null)
    });
  }
}

