import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Batch, PatchBatchRequest, CreateBatchRequest } from '../../models/batch.model';
import { environment } from '../../models/environment';

@Injectable({ providedIn: 'root' })
export class BatchService {
  private readonly base = `${environment.apiUrl}/products`;
  private readonly batchBase = `${environment.apiUrl}/batches`;

  constructor(private http: HttpClient) {}

  listByProduct(productId: string): Observable<Batch[]> {
    return this.http.get<Batch[]>(`${this.base}/${productId}/batches`);
  }

  create(productId: string, req: CreateBatchRequest): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(`${this.base}/${productId}/batches`, req);
  }

  patch(productId: string, batchId: number, fields: PatchBatchRequest): Observable<{ ok: boolean }> {
    return this.http.patch<{ ok: boolean }>(`${this.base}/${productId}/batches/${batchId}`, fields);
  }

  delete(productId: string, batchId: number): Observable<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(`${this.base}/${productId}/batches/${batchId}`);
  }

  getExpiring(days: number = 7): Observable<Batch[]> {
    const params = new HttpParams().set('days', days.toString());
    return this.http.get<Batch[]>(`${this.batchBase}/expiring`, { params });
  }
  
  getDepletedBatches(): Observable<Batch[]> {
    return this.http.get<Batch[]>('/api/batches/depleted');
  }

  validateDepletion(productFirestoreId: string, batchId: number): Observable<void> {
    return this.http.delete<void>(
      `/api/products/${productFirestoreId}/batches/${batchId}/validate-depletion`
    );
  }
}
