export interface Product {
  firestoreId: string;
  odooId?: number;
  name: string;
  code?: string;
  category?: string;
  unit?: string;
  source?: string;
}

export interface UpsertProductRequest {
  firestoreId: string;
  odooId?: number;
  name: string;
  code?: string;
  category?: string;
  unit?: string;
  source?: string;
}