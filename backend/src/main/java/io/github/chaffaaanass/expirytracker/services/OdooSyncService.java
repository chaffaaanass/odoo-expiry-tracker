package io.github.chaffaaanass.expirytracker.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.chaffaaanass.expirytracker.config.OdooSessionManager;
import io.github.chaffaaanass.expirytracker.dtos.OdooSessionData;
import io.github.chaffaaanass.expirytracker.dtos.ProductDtos;
import io.github.chaffaaanass.expirytracker.entities.Batch;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Replaces BackgroundService.dart (WorkManager) + parts of OdooService.dart.
 *
 * Scheduled tasks run server-side on a cron/fixed-rate basis.
 * They use a "system" OdooSessionData loaded from environment variables
 * (or the last credentials saved by any authenticated web user).
 *
 * For a multi-tenant setup you would store per-user credentials and fan out;
 * for the single-company use-case this matches the original Android behaviour.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OdooSyncService {

    private final OdooClient      odooClient;
    private final ProductService  productService;
    private final BatchService    batchService;

    private final OdooSessionManager sessionManager;

    // ── 1. Poll purchase orders every 15 minutes ──────────────────────────────

    @Scheduled(fixedRateString = "PT15M", initialDelayString = "PT1M")
    public void pollPurchaseOrders() {
        OdooSessionData session = sessionManager.getSession();
        if (session == null) {
            log.debug("pollPurchaseOrders: no active session, skipping");
            return;
        }
        try {
            odooClient.ensureAuthenticated(session);

            LocalDateTime since   = LocalDateTime.now().minusDays(1);

            List<Map<String, Object>> orders = odooClient.fetchPurchaseOrders(
                    session, since, 20);

            if (orders.isEmpty()) return;

            // Collect all line IDs to fetch in one batch
            List<Integer> allLineIds = orders.stream()
                    .flatMap(o -> {
                        Object lines = o.get("order_line");
                        if (lines instanceof List<?> l) {
                            return l.stream().map(id -> ((Number) id).intValue());
                        }
                        return java.util.stream.Stream.empty();
                    })
                    .collect(Collectors.toList());

            List<Map<String, Object>> lineRecords = allLineIds.isEmpty()
                    ? List.of()
                    : odooClient.fetchPurchaseOrderLines(session, allLineIds);

            log.info("pollPurchaseOrders: {} new PO(s) with {} lines",
                    orders.size(), lineRecords.size());

        } catch (Exception e) {
            log.warn("pollPurchaseOrders failed: {}", e.getMessage());
        }
    }

    // ── 2. Daily expiry check at 08:00 ───────────────────────────────────────

    @Scheduled(cron = "0 0 8 * * *")
    public void dailyExpiryCheck() {
        try {
            List<Batch> expiring = batchService.findExpiring(7);
            if (expiring.isEmpty()) return;

            // Group by threshold days (7, 3, 2, 1, 0) — same logic as
            log.info("dailyExpiryCheck: {} batch(es) expiring within 7 days", expiring.size());

            expiring.forEach(b -> {
                long days = LocalDate.now().until(b.getExpiryDate()).getDays();
                String level = days <= 0 ? "EXPIRED"
                        : days == 1     ? "CRITICAL"
                          : days <= 3     ? "WARNING"
                            : days <= 7     ? "SOON"
                              : "OK";
                log.info("  [{}] {} – lot {} exp {}",
                        level,
                        b.getProduct().getName(),
                        b.getBatchNumber(),
                        b.getExpiryDate());
            });

        } catch (Exception e) {
            log.warn("dailyExpiryCheck failed: {}", e.getMessage());
        }
    }

    // ── 3. Sync Odoo product catalogue ───────────────────────────────────────
    // Called manually via POST /api/odoo/sync-products (and optionally on login)

    public int syncProducts() {
        OdooSessionData session = sessionManager.getSession();
        odooClient.ensureAuthenticated(session);

        List<Map<String, Object>> odooProducts = odooClient.fetchProducts(session);

        List<ProductDtos.UpsertRequest> reqs = odooProducts.stream()
                .map(p -> {
                    ProductDtos.UpsertRequest req = new ProductDtos.UpsertRequest();

                    int id = ((Number) p.get("id")).intValue();
                    req.setFirestoreId("odoo_" + id);
                    req.setOdooId(id);
                    req.setName(Objects.toString(p.get("name"), ""));
                    req.setSource("odoo_product");

                    // default_code
                    Object code = p.get("default_code");
                    req.setCode(code instanceof String s ? s : "");

                    // categ_id → [id, "Name"]
                    Object categ = p.get("categ_id");
                    req.setCategory(lastStringOf(categ));

                    // uom_id → [id, "Name"]
                    Object uom = p.get("uom_id");
                    req.setUnit(lastStringOf(uom));

                    return req;
                })
                .collect(Collectors.toList());

        return productService.upsertBulk(reqs);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String lastStringOf(Object obj) {
        if (obj instanceof List<?> list && !list.isEmpty()) {
            return list.get(list.size() - 1).toString();
        }
        return "";
    }
}

