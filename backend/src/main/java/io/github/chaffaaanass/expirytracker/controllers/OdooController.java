package io.github.chaffaaanass.expirytracker.controllers;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import io.github.chaffaaanass.expirytracker.config.OdooSessionManager;
import io.github.chaffaaanass.expirytracker.dtos.OdooDtos;
import io.github.chaffaaanass.expirytracker.dtos.OdooSessionData;
import io.github.chaffaaanass.expirytracker.services.OdooClient;
import io.github.chaffaaanass.expirytracker.services.OdooSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/odoo")
@RequiredArgsConstructor
public class OdooController {

    private final OdooClient odooClient;
    private final OdooSyncService odooSyncService;
    private final OdooSessionManager sessionManager;

    // ── Products ─────────────────────────────────────────────────────────────

    /** Fetch raw Odoo products (no PG write) */
    @GetMapping("/products")
    public ResponseEntity<?> odooProducts(HttpSession session) {
        OdooSessionData s = sessionManager.getSession();
        List<Map<String, Object>> raw = odooClient.fetchProducts(s);

        List<OdooDtos.OdooProductDto> result = raw.stream().map(p -> {
            OdooDtos.OdooProductDto dto = new OdooDtos.OdooProductDto();
            int id = ((Number) p.get("id")).intValue();
            dto.setOdooId(id);
            dto.setFirestoreId("odoo_" + id);
            dto.setName(Objects.toString(p.get("name"), ""));
            dto.setSource("odoo_product");

            Object code = p.get("default_code");
            dto.setCode(code instanceof String str ? str : "");
            dto.setCategory(lastString(p.get("categ_id")));
            dto.setUnit(lastString(p.get("uom_id")));
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /** Pull Odoo products and upsert into PostgreSQL */
    @PostMapping("/sync-products")
    public ResponseEntity<?> syncProducts(HttpSession session) {
        int count = odooSyncService.syncProducts();
        return ResponseEntity.ok(Map.of("ok", true, "synced", count));
    }

    // ── Purchase Orders ───────────────────────────────────────────────────────

    @GetMapping("/purchase-orders")
    public ResponseEntity<?> purchaseOrders(
            HttpSession session,
            @RequestParam(defaultValue = "15") int days,
            @RequestParam(defaultValue = "20") int max) {

        OdooSessionData s = sessionManager.getSession();

        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Map<String, Object>> orders =
                odooClient.fetchPurchaseOrders(s, since, max);

        if (orders.isEmpty()) return ResponseEntity.ok(List.of());

        // Gather all line IDs and fetch in one call
        List<Integer> allLineIds = orders.stream()
                .flatMap(o -> {
                    Object lines = o.get("order_line");
                    if (lines instanceof List<?> l) {
                        return l.stream().map(id -> ((Number) id).intValue());
                    }
                    return java.util.stream.Stream.empty();
                })
                .collect(Collectors.toList());

        Map<Integer, List<OdooDtos.PurchaseOrderLineDto>> linesById = new HashMap<>();
        if (!allLineIds.isEmpty()) {
            List<Map<String, Object>> lineRecords =
                    odooClient.fetchPurchaseOrderLines(s, allLineIds);

            for (Map<String, Object> lr : lineRecords) {
                OdooDtos.PurchaseOrderLineDto lineDto = new OdooDtos.PurchaseOrderLineDto();
                lineDto.setId(((Number) lr.get("id")).intValue());

                // order_id → [id, "name"]
                Object orderId = lr.get("order_id");
                int oId = orderId instanceof List<?> ol
                        ? ((Number) ol.get(0)).intValue() : 0;

                // product_id → [id, "name"]
                Object prod = lr.get("product_id");
                if (prod instanceof List<?> pl && pl.size() == 2) {
                    lineDto.setProductId(((Number) pl.get(0)).intValue());
                    lineDto.setProductName(pl.get(1).toString());
                }
                lineDto.setOrderedQty(toDouble(lr.get("product_qty")));
                lineDto.setReceivedQty(toDouble(lr.get("qty_received")));

                linesById.computeIfAbsent(oId, k -> new ArrayList<>()).add(lineDto);
            }
        }

        // Build final DTO list
        List<OdooDtos.PurchaseOrderDto> result = orders.stream().map(o -> {
            OdooDtos.PurchaseOrderDto dto = new OdooDtos.PurchaseOrderDto();
            int id = ((Number) o.get("id")).intValue();
            dto.setId(id);
            dto.setName(Objects.toString(o.get("name"), ""));
            dto.setState(Objects.toString(o.get("state"), ""));

            Object partner = o.get("partner_id");
            dto.setSupplierName(partner instanceof List<?> pl && pl.size() == 2
                    ? pl.get(1).toString() : "");

            Object dateApprove = o.get("date_approve");
            dto.setApprovedAt(dateApprove != null && !Boolean.FALSE.equals(dateApprove)
                    ? dateApprove.toString() : null);

            dto.setLines(linesById.getOrDefault(id, List.of()));
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String lastString(Object obj) {
        if (obj instanceof List<?> list && !list.isEmpty()) {
            return list.get(list.size() - 1).toString();
        }
        return "";
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj instanceof String str) return Double.parseDouble(str);
        return 0.0;
    }

    // ── Exception ─────────────────────────────────────────────────────────────

    static class NotAuthenticatedException extends RuntimeException {
        NotAuthenticatedException() { super("Not authenticated"); }
    }
}
