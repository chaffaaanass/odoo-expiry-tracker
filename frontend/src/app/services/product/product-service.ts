import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product, UpsertProductRequest } from '../../models/product.model';
import { environment } from '../../models/environment';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly base = `${environment.apiUrl}/products`;

  constructor(private http: HttpClient) {}

  listAll(): Observable<Product[]> {
    return this.http.get<Product[]>(this.base);
  }

  upsert(req: UpsertProductRequest): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(`${this.base}/upsert`, req);
  }

  upsertBulk(reqs: UpsertProductRequest[]): Observable<{ ok: boolean; count: number }> {
    return this.http.post<{ ok: boolean; count: number }>(`${this.base}/upsert-bulk`, reqs);
  }
}
