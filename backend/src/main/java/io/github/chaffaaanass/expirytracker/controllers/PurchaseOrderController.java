package io.github.chaffaaanass.expirytracker.controllers;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.chaffaaanass.expirytracker.config.OdooSessionManager;
import io.github.chaffaaanass.expirytracker.dtos.BatchDtos;
import io.github.chaffaaanass.expirytracker.dtos.OdooDtos;
import io.github.chaffaaanass.expirytracker.dtos.OdooSessionData;
import io.github.chaffaaanass.expirytracker.dtos.ProductDtos;
import io.github.chaffaaanass.expirytracker.entities.Batch;
import io.github.chaffaaanass.expirytracker.services.BatchService;
import io.github.chaffaaanass.expirytracker.services.OdooClient;
import io.github.chaffaaanass.expirytracker.services.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Everything the PurchaseOrderScreen + AddBatchScreen need, in one controller.
 *
 * The Android flow is:
 *   1.  Screen opens → fetch POs from Odoo  (GET /api/purchase-orders)
 *   2.  User clicks "+ Lot" on a PO line   → save batch to PostgreSQL
 *                                             (POST /api/purchase-orders/{poName}/lines/{lineId}/batch)
 *       - the product is auto-upserted in PG so the batch FK resolves
 *   3.  User edits the batch              → PATCH /api/purchase-orders/{poName}/lines/{lineId}/batch/{batchId}
 *   4.  User deletes the batch            → DELETE /api/purchase-orders/{poName}/lines/{lineId}/batch/{batchId}
 *   5.  User clicks "Ignorer" or "Done"   → POST /api/purchase-orders/mark-seen { ids:[…] }
 *
 * Because the batch is linked to the product via product_firestore_id (= "odoo_{odooId}"),
 * we auto-upsert the product on every "+ Lot" call so we never get a FK violation.
 */
@Slf4j
@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final OdooClient       odooClient;
    private final ProductService   productService;
    private final BatchService     batchService;
    private final OdooSessionManager sessionManager;


    @GetMapping
    public ResponseEntity<?> listPurchaseOrders(
            @RequestParam(defaultValue = "15") int days,
            @RequestParam(defaultValue = "100") int maxResults) {

        OdooSessionData s = sessionManager.getSession();

        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Map<String, Object>> rawOrders =
                odooClient.fetchPurchaseOrders(s, since, maxResults);

        if (rawOrders.isEmpty()) return ResponseEntity.ok(List.of());
        if (rawOrders.isEmpty()) return ResponseEntity.ok(List.of());

        // Collect every order_line ID across all POs in one shot,
        // then fetch them all in a single Odoo call (same as Dart's _fetchOrderLines)
        List<Integer> allLineIds = rawOrders.stream()
                .flatMap(o -> {
                    Object lines = o.get("order_line");
                    if (lines instanceof List<?> l) {
                        return l.stream().map(id -> ((Number) id).intValue());
                    }
                    return java.util.stream.Stream.empty();
                })
                .collect(Collectors.toList());

        // Group lines by their parent order id so we can attach them below
        Map<Integer, List<OdooDtos.PurchaseOrderLineDto>> linesByOrderId = new HashMap<>();
        if (!allLineIds.isEmpty()) {
            for (Map<String, Object> rl : odooClient.fetchPurchaseOrderLines(s, allLineIds)) {
                OdooDtos.PurchaseOrderLineDto dto = mapLine(rl);
                linesByOrderId
                        .computeIfAbsent(dto.getOrderId(), k -> new ArrayList<>())
                        .add(dto);
            }
        }

        List<OdooDtos.PurchaseOrderDto> result = rawOrders.stream().map(o -> {
            int id = ((Number) o.get("id")).intValue();

            OdooDtos.PurchaseOrderDto dto = new OdooDtos.PurchaseOrderDto();
            dto.setId(id);
            dto.setName(Objects.toString(o.get("name"), ""));
            dto.setState(Objects.toString(o.get("state"), ""));

            Object partner = o.get("partner_id");
            dto.setSupplierName(partner instanceof List<?> pl && pl.size() == 2
                    ? pl.get(1).toString() : "");

            Object da = o.get("date_approve");
            Object dorder = o.get("date_order");
            String resolvedDate = (da != null && !Boolean.FALSE.equals(da))
                    ? da.toString()
                    : (dorder != null && !Boolean.FALSE.equals(dorder) ? dorder.toString() : null);
            dto.setApprovedAt(resolvedDate);

            dto.setLines(linesByOrderId.getOrDefault(id, List.of()));
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // POST /api/purchase-orders/{poName}/batches
    //
    // User clicked "+ Lot" on a PO line and submitted the AddBatchScreen form.
    // Body carries:
    //   - odooProductId + productName  (from the PO line — to auto-upsert the product)
    //   - batchNumber, expiryDate, deliveryDate, quantity, notes  (batch form fields)
    //
    // purchaseOrderId is always taken from {poName} in the path — the form never sends it.
    // =========================================================================
    @PostMapping("/{poName}/batches")
    public ResponseEntity<?> addBatch(
            @PathVariable String poName,
            @Valid @RequestBody BatchDtos.AddBatchRequest req) {

        // Auto-upsert the product so the FK on batches.product_firestore_id never fails.
        // A PO line only gives us odooProductId + productName — that is all we store.
        String firestoreId = "odoo_" + req.getOdooProductId();
        ensureProductExists(firestoreId, req.getOdooProductId(), req.getProductName());

        // Map form fields to the batch create request;
        // purchaseOrderId is locked to the path variable, not whatever the client might send.
        BatchDtos.CreateRequest batchReq = new BatchDtos.CreateRequest();
        batchReq.setBatchNumber(req.getBatchNumber());
        batchReq.setExpiryDate(req.getExpiryDate());
        batchReq.setDeliveryDate(req.getDeliveryDate());
        batchReq.setQuantity(req.getQuantity());
        batchReq.setNotes(req.getNotes());
        batchReq.setPurchaseOrderId(poName);

        try {
            Batch saved = batchService.create(firestoreId, batchReq);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("id", saved.getId(), "firestoreId", firestoreId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================================
    // PATCH /api/purchase-orders/{poName}/batches/{batchId}
    //
    // User edited a batch they previously added from this PO.
    // Body: { odooProductId: 123, fields: { batchNumber: "...", expiryDate: "..." } }
    // odooProductId is required to build firestoreId for the ownership check in BatchService.
    // =========================================================================
    @PatchMapping("/{poName}/batches/{batchId}")
    public ResponseEntity<?> editBatch(
            HttpSession session,
            @PathVariable Long   batchId,
            @RequestBody BatchDtos.PatchBatchRequest req) {

        if (req.getFields() == null || req.getFields().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No fields provided"));
        }

        String firestoreId = "odoo_" + req.getOdooProductId();

        try {
            batchService.patch(firestoreId, batchId, req.getFields());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================================
    // DELETE /api/purchase-orders/{poName}/batches/{batchId}
    //
    // User deleted a batch from this PO.
    // Body: { odooProductId: 123 } — needed to resolve firestoreId for the FK check.
    // =========================================================================
    @DeleteMapping("/{poName}/batches/{batchId}")
    public ResponseEntity<?> deleteBatch(
            @PathVariable String  poName,
            @PathVariable Long    batchId,
            @RequestBody  Map<String, Integer> body) {


        Integer odooProductId = body.get("odooProductId");
        if (odooProductId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "odooProductId required"));
        }

        String firestoreId = "odoo_" + odooProductId;

        try {
            batchService.delete(firestoreId, batchId);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================================
    // GET /api/purchase-orders/{poName}/batches?odooProductId=123
    //
    // Returns the batch(es) saved for a specific product on this PO.
    // Called right after AddBatchScreen closes so the card can show Edit/Delete.
    // Mirrors: pg.fetchBatches(firestoreId).where((b) => b.purchaseOrderId == poName)
    // =========================================================================
    @GetMapping("/{poName}/batches")
    public ResponseEntity<?> getBatchesForProduct(
            @PathVariable String poName,
            @RequestParam  int    odooProductId) {


        String firestoreId = "odoo_" + odooProductId;
        List<Batch> batches = batchService.findByProductAndPurchaseOrder(firestoreId, poName);
        return ResponseEntity.ok(batches);
    }


    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Ensures the product row exists in PG before attaching a batch to it.
     * A PO line only gives us odooProductId + productName — no category, no unit.
     * source = "purchase_order" distinguishes these from fully-synced products.
     */
    private void ensureProductExists(String firestoreId, int odooId, String name) {
        ProductDtos.UpsertRequest req = new ProductDtos.UpsertRequest();
        req.setFirestoreId(firestoreId);
        req.setOdooId(odooId);
        req.setName(name == null || name.isBlank() ? "Produit #" + odooId : name);
        req.setSource("purchase_order");
        productService.upsert(req);
    }

    private OdooDtos.PurchaseOrderLineDto mapLine(Map<String, Object> rl) {
        OdooDtos.PurchaseOrderLineDto dto = new OdooDtos.PurchaseOrderLineDto();
        dto.setId(((Number) rl.get("id")).intValue());

        // order_id comes from Odoo as [orderId, "PO/001"]
        Object orderId = rl.get("order_id");
        dto.setOrderId(orderId instanceof List<?> ol ? ((Number) ol.get(0)).intValue() : 0);

        // product_id comes from Odoo as [productId, "Product Name"]
        Object prod = rl.get("product_id");
        if (prod instanceof List<?> pl && pl.size() == 2) {
            dto.setProductId(((Number) pl.get(0)).intValue());
            dto.setProductName(pl.get(1).toString());
        }

        dto.setOrderedQty(toDouble(rl.get("product_qty")));
        dto.setReceivedQty(toDouble(rl.get("qty_received")));
        return dto;
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj instanceof String str) return Double.parseDouble(str);
        return 0.0;
    }
}

