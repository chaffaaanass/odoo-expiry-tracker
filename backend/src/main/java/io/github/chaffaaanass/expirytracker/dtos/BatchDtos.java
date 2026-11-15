package io.github.chaffaaanass.expirytracker.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

public class BatchDtos {
    @Data
    public static class CreateRequest {
        @NotBlank String batchNumber;
        @NotNull LocalDate expiryDate;
        LocalDate deliveryDate;
        double quantity;
        String purchaseOrderId;
        String notes;
    }


    @Data
    public static class AddBatchRequest {
        @NotNull
        Integer   odooProductId;  // Odoo product.product id from the PO line
        @NotBlank String    productName;    // displayed name from the PO line

        @NotBlank String    batchNumber;
        @NotNull  LocalDate expiryDate;
        LocalDate deliveryDate;   // nullable — same as Android
        @NotNull  Double    quantity;
        String    notes;          // nullable
    }

    /**
     * Body for PATCH /{poName}/batches/{batchId}.
     * odooProductId resolves the firestoreId for the ownership check.
     * fields is the free-form patch map (same pattern as the generic batch PATCH).
     */
    @Data
    public static class PatchBatchRequest {
        @NotNull Integer             odooProductId;
        @NotNull Map<String, Object> fields;
    }
}
