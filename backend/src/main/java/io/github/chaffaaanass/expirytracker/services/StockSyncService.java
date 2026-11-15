package io.github.chaffaaanass.expirytracker.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.chaffaaanass.expirytracker.config.OdooSessionManager;
import io.github.chaffaaanass.expirytracker.dtos.OdooSessionData;
import io.github.chaffaaanass.expirytracker.entities.Batch;
import io.github.chaffaaanass.expirytracker.entities.Product;
import io.github.chaffaaanass.expirytracker.entities.StockSnapshot;
import io.github.chaffaaanass.expirytracker.repositories.BatchRepository;
import io.github.chaffaaanass.expirytracker.repositories.ProductRepository;
import io.github.chaffaaanass.expirytracker.repositories.StockSnapshotRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSyncService {

    private final OdooClient            odooClient;
    private final OdooSessionManager    sessionManager;
    private final ProductRepository     productRepository;
    private final BatchRepository       batchRepository;
    private final StockSnapshotRepository snapshotRepository;

    /**
     * Runs every 5 minutes by default.
     * Override with sync.stock.interval-ms in application.properties.
     */
    @Scheduled(fixedDelayString = "${sync.stock.interval-ms:300000}")
    @Transactional
    public void syncStock() {
        log.info("═══ Stock sync started ═══");

        OdooSessionData session = sessionManager.getSession();

        List<Product> products = productRepository.findAllWithOdooIdAndBatches();
        if (products.isEmpty()) {
            log.info("No products with odooId found, skipping sync");
            return;
        }

        log.info("Syncing {} products...", products.size());

        List<Integer> odooIds = products.stream()
                .map(Product::getOdooId)
                .collect(Collectors.toList());

        // Build a map: odooId → currentQty from Odoo
        Map<Integer, Double> currentQtys = odooClient
                .fetchStockQuantities(session, odooIds)
                .stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r.get("id")).intValue(),
                        r -> ((Number) r.get("qty_available")).doubleValue()
                ));

        for (Product product : products) {
            Integer odooId = product.getOdooId();
            Double currentQty = currentQtys.get(odooId);

            if (currentQty == null) {
                log.info("  [{}] '{}' — not found in Odoo response, skipping",
                        odooId, product.getName());
                continue;
            }

            StockSnapshot snapshot = snapshotRepository.findById(odooId).orElse(null);

            if (snapshot == null) {
                log.info("  [{}] '{}' — first sync, baseline qty={} → reconciling",
                        odooId, product.getName(), currentQty);
                snapshotRepository.save(new StockSnapshot(odooId, currentQty));
                reconcile(product, currentQty);
                continue;
            }

            double delta = snapshot.getLastKnownQty() - currentQty;

            if (delta > 0.001) {
                log.info("  [{}] '{}' — decreased by {} (was={} now={})",
                        odooId, product.getName(), delta,
                        snapshot.getLastKnownQty(), currentQty);
                deductFromBatches(product.getFirestoreId(), delta);
            } else if (delta < -0.001) {
                log.info("  [{}] '{}' — increased by {} (was={} now={})",
                        odooId, product.getName(), Math.abs(delta),
                        snapshot.getLastKnownQty(), currentQty);
                restockBatches(product.getFirestoreId(), Math.abs(delta));
            } else {
                log.debug("  [{}] '{}' — no change (qty={})",
                        odooId, product.getName(), currentQty);
            }

            reconcile(product, currentQty);

            snapshot.setLastKnownQty(currentQty);
            snapshot.setUpdatedAt(Instant.now());
            snapshotRepository.save(snapshot);

        }
        log.info("═══ Stock sync finished ═══");
    }

    private void reconcile(Product product, double odooQty) {
        List<Batch> batches = batchRepository
                .findByProduct_FirestoreIdOrderByExpiryDateAsc(product.getFirestoreId());

        double systemTotal = batches.stream()
                .filter(b -> !"depleted".equals(b.getStatus()))
                .mapToDouble(Batch::getQuantity)
                .sum();

        double diff = odooQty - systemTotal;

        if (Math.abs(diff) < 0.001) {
            log.info("    ✓ [{}] '{}' in sync — Odoo={} System={}",
                    product.getOdooId(), product.getName(), odooQty, systemTotal);
            return;
        }

        if (diff > 0) {
            log.info("    ↑ [{}] '{}' drift corrected — Odoo={} System={} → adding {}",
                    product.getOdooId(), product.getName(), odooQty, systemTotal, diff);
            restockBatches(product.getFirestoreId(), diff);
        } else {
            log.info("    ↓ [{}] '{}' drift corrected — Odoo={} System={} → removing {}",
                    product.getOdooId(), product.getName(), odooQty, systemTotal, Math.abs(diff));
            deductFromBatches(product.getFirestoreId(), Math.abs(diff));
        }
    }

    private void deductFromBatches(String firestoreId, double toDeduct) {
        List<Batch> batches = batchRepository
                .findByProduct_FirestoreIdOrderByExpiryDateAsc(firestoreId);

        for (Batch batch : batches) {
            if (toDeduct <= 0) break;
            if ("depleted".equals(batch.getStatus())) continue;

            double available = batch.getQuantity();
            if (available <= 0) continue;

            if (available >= toDeduct) {
                double newQty = available - toDeduct;
                batch.setQuantity(newQty);
                if (newQty < 0.001) {
                    batch.setStatus("depleted");
                    log.info("Batch id={} lot='{}' reached 0 — marked as depleted", batch.getId(), batch.getBatchNumber());
                }
                toDeduct = 0;
            } else {
                toDeduct -= available;
                batch.setQuantity(0.0);
                batch.setStatus("depleted");
                log.info("Batch id={} lot='{}' fully depleted (had {})", batch.getId(), batch.getBatchNumber(), available);
            }

            batchRepository.save(batch);
        }

        if (toDeduct > 0.001) {
            log.warn("firestoreId={} — {} units unaccounted for across all batches", firestoreId, toDeduct);
        }
    }

    /**
     * Adds quantity back to batches using reverse-FEFO:
     * the soonest-expiring batch (the one last deducted from) gets restocked first.
     * If it was depleted, it gets reactivated.
     */
    private void restockBatches(String firestoreId, double toAdd) {
        List<Batch> batches = batchRepository
                .findByProduct_FirestoreIdOrderByExpiryDateAsc(firestoreId);

        if (batches.isEmpty()) {
            log.warn("firestoreId={} — restock of {} units but no batches found", firestoreId, toAdd);
            return;
        }

        // Add to the soonest-expiring batch first (same one that was deducted from)
        for (Batch batch : batches) {
            if (toAdd <= 0) break;

            batch.setQuantity(batch.getQuantity() + toAdd);

            // If it was depleted, reactivate it
            if ("depleted".equals(batch.getStatus())) {
                batch.setStatus("active");
                log.info("Batch id={} lot='{}' reactivated after restock", batch.getId(), batch.getBatchNumber());
            }

            log.info("Batch id={} lot='{}' restocked by {} → new qty={}",
                    batch.getId(), batch.getBatchNumber(), toAdd, batch.getQuantity());

            toAdd = 0; // whole amount goes to first batch
            batchRepository.save(batch);
        }
    }
}