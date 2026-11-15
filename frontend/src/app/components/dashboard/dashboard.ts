import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { BatchService } from '../../services/batch/batch-service';
import { ProductService } from '../../services/product/product-service';
import { Batch } from '../../models/batch.model';
import { Product } from '../../models/product.model';
import { daysUntilExpiry, getExpiryStatus } from '../../models/ui-helpers.model';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  expiringBatches = signal<Batch[]>([]);
  depletedBatches = signal<Batch[]>([]);
  products = signal<Product[]>([]);
  loading = signal(true);
  validatingId = signal<number | null>(null);
  validatingDepletedId = signal<number | null>(null);

  readonly daysUntilExpiry = daysUntilExpiry;
  readonly getExpiryStatus = getExpiryStatus;

  stats = signal({
    total: 0,
    critical: 0,
    warning: 0,
    expired: 0
  });

  constructor(
    private batchService: BatchService,
    private productService: ProductService
  ) {}

  ngOnInit(): void {
    this.productService.listAll().subscribe(p => this.products.set(p));

    this.batchService.getExpiring(30).subscribe({
      next: batches => {
        this.expiringBatches.set(batches);
        this.stats.set({
          total: batches.length,
          critical: batches.filter(b => getExpiryStatus(b.expiryDate) === 'critical').length,
          warning: batches.filter(b => getExpiryStatus(b.expiryDate) === 'warning').length,
          expired: batches.filter(b => getExpiryStatus(b.expiryDate) === 'expired').length
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });

    this.batchService.getDepletedBatches().subscribe({
      next: batches => this.depletedBatches.set(batches),
      error: () => {}  
    });
  }

  validate(batch: Batch): void {
    if (!confirm(`Confirm removal of lot ${batch.batchNumber} from shelves?`)) return;
    this.validatingId.set(batch.id);
    this.batchService.delete(batch.productFirestoreId!, batch.id).subscribe({
      next: () => {
        this.validatingId.set(null);
        const batches = this.expiringBatches().filter(b => b.id !== batch.id);
        this.expiringBatches.set(batches);
        this.stats.set({
          total:    batches.length,
          critical: batches.filter(b => getExpiryStatus(b.expiryDate) === 'critical').length,
          warning:  batches.filter(b => getExpiryStatus(b.expiryDate) === 'warning').length,
          expired:  batches.filter(b => getExpiryStatus(b.expiryDate) === 'expired').length
        });
      },
      error: () => this.validatingId.set(null)
    });
  }

  validateDepletion(batch: Batch): void {
    if (!confirm(`Confirm that lot ${batch.batchNumber} is fully consumed and remove it?`)) return;
    this.validatingDepletedId.set(batch.id);
    this.batchService.validateDepletion(batch.productFirestoreId!, batch.id).subscribe({
      next: () => {
        this.validatingDepletedId.set(null);
        this.depletedBatches.update(list => list.filter(b => b.id !== batch.id));
      },
      error: () => this.validatingDepletedId.set(null)
    });
  }
}

