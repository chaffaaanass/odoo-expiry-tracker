export interface AddBatchRequest {
  odooProductId: number;
  productName: string;
  batchNumber: string;
  expiryDate: string;       
  deliveryDate?: string;
  quantity: number;
  notes?: string;
}

export interface PatchBatchRequest {
  odooProductId: number;
  fields: Record<string, unknown>;
}

export interface DeleteBatchRequest {
  odooProductId: number;
}

export interface AddBatchResponse {
  id: number;
  firestoreId: string;
}