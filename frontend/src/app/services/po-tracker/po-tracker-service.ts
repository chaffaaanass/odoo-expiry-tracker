import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../models/environment';

@Injectable({ providedIn: 'root' })
export class PoTrackerService {
  private readonly base = `${environment.apiUrl}/po-tracker`;

  constructor(private http: HttpClient) {}

  getTrackedIds(): Observable<number[]> {
    return this.http.get<number[]>(this.base);
  }

  markSeen(ids: number[]): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(`${this.base}/mark-seen`, { ids });
  }
}
