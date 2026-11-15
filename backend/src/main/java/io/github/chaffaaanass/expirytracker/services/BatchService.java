package io.github.chaffaaanass.expirytracker.services;


import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import io.github.chaffaaanass.expirytracker.dtos.BatchDtos;
import io.github.chaffaaanass.expirytracker.entities.Batch;
import io.github.chaffaaanass.expirytracker.entities.Product;
import io.github.chaffaaanass.expirytracker.repositories.BatchRepository;
import io.github.chaffaaanass.expirytracker.repositories.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Batch> findByProduct(String productId) {
        return batchRepository.findByProduct_FirestoreIdOrderByExpiryDateAsc(productId);
    }

    @Transactional(readOnly = true)
    public List<Batch> findByProductAndPurchaseOrder(String productId, String purchaseOrderId) {
        return batchRepository
                .findByProduct_FirestoreIdAndPurchaseOrderIdOrderByCreatedAtDesc(
                        productId, purchaseOrderId);
    }

    @Transactional
    public Batch create(String productId, BatchDtos.CreateRequest req) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

        Batch batch = Batch.builder()
                .product(product)
                .batchNumber(req.getBatchNumber())
                .expiryDate(req.getExpiryDate())
                .deliveryDate(req.getDeliveryDate())
                .quantity(req.getQuantity())
                .purchaseOrderId(req.getPurchaseOrderId())
                .notes(req.getNotes())
                .build();

        return batchRepository.save(batch);
    }

    /**
     * Partial update — mirrors the Node.js PATCH with dynamic SET clause.
     * Accepts a free-form map of field names (snake_case or camelCase).
     */
    @Transactional
    public void patch(String productId, Long batchId, Map<String, Object> fields) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));

        // Validate the batch belongs to the given product
        if (!batch.getProduct().getFirestoreId().equals(productId)) {
            throw new IllegalArgumentException("Batch does not belong to product");
        }

        // Apply each field dynamically
        fields.forEach((key, value) -> applyField(batch, key, value));
        batchRepository.save(batch);
    }

    @Transactional
    public void delete(String productId, Long batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));
        if (!batch.getProduct().getFirestoreId().equals(productId)) {
            throw new IllegalArgumentException("Batch does not belong to product");
        }
        batchRepository.delete(batch);
    }

    /**
     * All batches expiring within [days] days — for dashboard & scheduled jobs.
     */
    @Transactional(readOnly = true)
    public List<Batch> findExpiring(int days) {
        return batchRepository.findExpiringAndExpired(LocalDate.now().plusDays(days));
    }

    @Transactional(readOnly = true)
    public List<Batch> findDepleted() {
        return batchRepository.findAllByStatus("depleted");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void applyField(Batch batch, String rawKey, Object value) {
        // Normalise snake_case → camelCase
        String key = toCamel(rawKey);
        try {
            switch (key) {
                case "batchNumber"    -> batch.setBatchNumber((String) value);
                case "expiryDate"    -> batch.setExpiryDate(LocalDate.parse(value.toString()));
                case "deliveryDate"  -> batch.setDeliveryDate(value == null ? null :
                        LocalDate.parse(value.toString()));
                case "quantity"      -> batch.setQuantity(Double.parseDouble(value.toString()));
                case "purchaseOrderId" -> batch.setPurchaseOrderId((String) value);
                case "notes"         -> batch.setNotes((String) value);
                default -> {} // ignore unknown fields
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid value for field " + rawKey + ": " + value);
        }
    }

    private String toCamel(String snake) {
        if (!snake.contains("_")) return snake;
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') { upper = true; }
            else { sb.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return sb.toString();
    }
}

