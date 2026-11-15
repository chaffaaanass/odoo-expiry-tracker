package io.github.chaffaaanass.expirytracker.dtos;

import lombok.Data;

import java.util.List;

public class OdooDtos {

    @Data
    public static class PurchaseOrderDto {
        private int id;
        private String name;
        private String supplierName;
        private String approvedAt;
        private String state;
        private List<PurchaseOrderLineDto> lines;
    }

    @Data
    public static class PurchaseOrderLineDto {
        private int id;
        private int orderId;
        private int productId;
        private String productName;
        private double orderedQty;
        private double receivedQty;
    }

    /** Response for GET /api/odoo/products */
    @Data
    public static class OdooProductDto {
        private int odooId;
        private String name;
        private String code;
        private String category;
        private String unit;
        private String source;
        private String firestoreId;
    }
}
