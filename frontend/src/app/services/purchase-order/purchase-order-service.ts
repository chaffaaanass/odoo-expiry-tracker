import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AddBatchRequest,
  AddBatchResponse,
  DeleteBatchRequest,
  PatchBatchRequest
} from '../../models/purchase-order.model'
import { Batch } from '../../models/batch.model'
import { PurchaseOrderDto } from '../../models/odoo.model';
import { environment } from '../../models/environment';

@Injectable({
  providedIn: 'root',
})
export class PurchaseOrderService {
  private readonly base = `${environment.apiUrl}/purchase-orders`;

  constructor(private http: HttpClient) {}

  // ── 1. GET /api/purchase-orders ──────────────────────────────────────────
  // Fetches confirmed POs from Odoo with all lines.
  // Pass already-seen IDs as exclude so they don't reappear.

  list(options: { days?: number; maxResults?: number; exclude?: string } = {}): Observable<PurchaseOrderDto[]> {
    let params = new HttpParams();
    if (options.days != null)       params = params.set('days', options.days.toString());
    if (options.maxResults != null) params = params.set('maxResults', options.maxResults.toString());
    if (options.exclude)            params = params.set('exclude', options.exclude);
    return this.http.get<PurchaseOrderDto[]>(this.base, { params });
  }

  // ── 2. POST /api/purchase-orders/{poName}/batches ────────────────────────
  // User clicked "+ Lot" and submitted the form.
  // Body carries odooProductId + productName (from the PO line) + batch form fields.
  // Backend auto-upserts the product and locks purchaseOrderId to poName.

  addBatch(poName: string, req: AddBatchRequest): Observable<AddBatchResponse> {
    return this.http.post<AddBatchResponse>(
      `${this.base}/${encodeURIComponent(poName)}/batches`,
      req
    );
  }

  // ── 3. PATCH /api/purchase-orders/{poName}/batches/{batchId} ─────────────
  // Edit an existing batch.
  // Body: { odooProductId: 123, fields: { batchNumber: "...", ... } }

  editBatch(poName: string, batchId: number, req: PatchBatchRequest): Observable<{ ok: boolean }> {
    return this.http.patch<{ ok: boolean }>(
      `${this.base}/${encodeURIComponent(poName)}/batches/${batchId}`,
      req
    );
  }

  // ── 4. DELETE /api/purchase-orders/{poName}/batches/{batchId} ────────────
  // Delete a batch. Body: { odooProductId: 123 }

  deleteBatch(poName: string, batchId: number, req: DeleteBatchRequest): Observable<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(
      `${this.base}/${encodeURIComponent(poName)}/batches/${batchId}`,
      { body: req }
    );
  }

  // ── 5. GET /api/purchase-orders/{poName}/batches?odooProductId=123 ────────
  // Fetch batches saved for a specific product on this PO.
  // Called after the form closes to show Edit/Delete on the card.

  getBatchesForProduct(poName: string, odooProductId: number): Observable<Batch[]> {
    const params = new HttpParams().set('odooProductId', odooProductId.toString());
    return this.http.get<Batch[]>(
      `${this.base}/${encodeURIComponent(poName)}/batches`,
      { params }
    );
  }
}
