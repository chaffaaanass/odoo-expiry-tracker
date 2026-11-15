import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OdooService } from '../../services/odoo/odoo-service';
import { PoTrackerService } from '../../services/po-tracker/po-tracker-service';
import { ProductService } from '../../services/product/product-service';
import { OdooProductDto, PurchaseOrderDto } from '../../models/odoo.model';

@Component({
  selector: 'app-odoo',
  imports: [CommonModule],
  templateUrl: './odoo.html',
  styleUrl: './odoo.scss',
})
export class Odoo {
  odooProducts = signal<OdooProductDto[]>([]);
  purchaseOrders = signal<PurchaseOrderDto[]>([]);
  trackedIds = signal<number[]>([]);

  syncing = signal(false);
  loadingProducts = signal(false);
  loadingPOs = signal(false);

  syncResult = signal<{ ok: boolean; synced: number } | null>(null);
  error = signal<string | null>(null);

  poFilterDays = signal(15);

  constructor(
    private odooService: OdooService,
    private poTrackerService: PoTrackerService,
    private productService: ProductService
  ) {}

  loadOdooProducts(): void {
    this.loadingProducts.set(true);
    this.odooService.getProducts().subscribe({
      next: p => { this.odooProducts.set(p); this.loadingProducts.set(false); },
      error: err => { this.error.set(err.error?.error ?? 'Failed'); this.loadingProducts.set(false); }
    });
  }

  syncProducts(): void {
    this.syncing.set(true);
    this.syncResult.set(null);
    this.odooService.syncProducts().subscribe({
      next: r => { this.syncResult.set(r); this.syncing.set(false); },
      error: err => { this.error.set(err.error?.error ?? 'Sync failed'); this.syncing.set(false); }
    });
  }

  loadPurchaseOrders(): void {
    this.loadingPOs.set(true);
    this.poTrackerService.getTrackedIds().subscribe(ids => {
      this.trackedIds.set(ids);
      this.odooService.getPurchaseOrders({
        days: this.poFilterDays(),
        exclude: ids.join(',')
      }).subscribe({
        next: pos => { this.purchaseOrders.set(pos); this.loadingPOs.set(false); },
        error: err => { this.error.set(err.error?.error ?? 'Failed'); this.loadingPOs.set(false); }
      });
    });
  }

  markAllSeen(): void {
    const ids = this.purchaseOrders().map(p => p.id);
    if (!ids.length) return;
    this.poTrackerService.markSeen(ids).subscribe(() => {
      this.trackedIds.update(prev => [...prev, ...ids]);
      this.purchaseOrders.set([]);
    });
  }

  markSeen(id: number): void {
    this.poTrackerService.markSeen([id]).subscribe(() => {
      this.trackedIds.update(prev => [...prev, id]);
      this.purchaseOrders.update(pos => pos.filter(p => p.id !== id));
    });
  }

  isTracked(id: number): boolean {
    return this.trackedIds().includes(id);
  }

  setDays(days: number): void {
    this.poFilterDays.set(days);
  }
}
