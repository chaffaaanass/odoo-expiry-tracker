import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OdooProductDto, PurchaseOrderDto } from '../../models/odoo.model';
import { environment } from '../../models/environment';

@Injectable({ providedIn: 'root' })
export class OdooService {
  private readonly base = `${environment.apiUrl}/odoo`;

  constructor(private http: HttpClient) {}

  getProducts(): Observable<OdooProductDto[]> {
    return this.http.get<OdooProductDto[]>(`${this.base}/products`);
  }

  syncProducts(): Observable<{ ok: boolean; synced: number }> {
    return this.http.post<{ ok: boolean; synced: number }>(`${this.base}/sync-products`, {});
  }

  getPurchaseOrders(options: {
    days?: number;
    max?: number;
    exclude?: string;
  } = {}): Observable<PurchaseOrderDto[]> {
    let params = new HttpParams();
    if (options.days != null) params = params.set('days', options.days.toString());
    if (options.max != null) params = params.set('max', options.max.toString());
    if (options.exclude) params = params.set('exclude', options.exclude);
    return this.http.get<PurchaseOrderDto[]>(`${this.base}/purchase-orders`, { params });
  }
}
