export interface Batch {
  id: number;
  batchNumber: string;
  expiryDate: string;       
  deliveryDate?: string;
  quantity: number;
  unit?: string;
  purchaseOrderId?: string;
  notes?: string;
  createdAt: string;
  productName?: string;
  productFirestoreId?: string; 
  status?: string;
}

export interface CreateBatchRequest {
  batchNumber: string;
  expiryDate: string;
  deliveryDate?: string;
  quantity: number;
  unit?: string;
  purchaseOrderId?: string;
  notes?: string;
}

export interface PatchBatchRequest {
  [key: string]: unknown;
}