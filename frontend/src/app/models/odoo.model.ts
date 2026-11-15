import { Batch } from "./batch.model";

export interface OdooProductDto {
  odooId: number;
  name: string;
  code: string;
  category: string;
  unit: string;
  source: string;
  firestoreId: string;
}

export interface PurchaseOrderLineDto {
  id: number;
  orderId: number; 
  productId: number;
  productName: string;
  orderedQty: number;
  receivedQty: number;
  batches?: Batch[];
  loadingBatches?: boolean;
  showBatchForm?: boolean;
}

export interface PurchaseOrderDto {
  id: number;
  name: string;
  supplierName: string;
  approvedAt: string | null;
  state: string;
  lines: PurchaseOrderLineDto[];
}