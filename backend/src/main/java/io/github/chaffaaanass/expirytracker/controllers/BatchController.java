package io.github.chaffaaanass.expirytracker.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.github.chaffaaanass.expirytracker.dtos.BatchDtos;
import io.github.chaffaaanass.expirytracker.entities.Batch;
import io.github.chaffaaanass.expirytracker.repositories.BatchRepository;
import io.github.chaffaaanass.expirytracker.services.BatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;
    private final BatchRepository batchRepository;

    @GetMapping("/api/products/{productId}/batches")
    public List<Batch> listBatches(@PathVariable String productId) {
        return batchService.findByProduct(productId);
    }

    @PostMapping("/api/products/{productId}/batches")
    public ResponseEntity<?> addBatch(
            @PathVariable String productId,
            @Valid @RequestBody BatchDtos.CreateRequest req) {
        try {
            Batch saved = batchService.create(productId, req);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("id", saved.getId()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/api/products/{productId}/batches/{batchId}")
    public ResponseEntity<?> patchBatch(
            @PathVariable String productId,
            @PathVariable Long   batchId,
            @RequestBody  Map<String, Object> fields) {

        if (fields == null || fields.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No fields provided"));
        }
        try {
            batchService.patch(productId, batchId, fields);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/products/{productId}/batches/{batchId}")
    public ResponseEntity<?> deleteBatch(
            @PathVariable String productId,
            @PathVariable Long   batchId) {
        try {
            batchService.delete(productId, batchId);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Cross-product expiring query ─────────────────────────────────────────

    @GetMapping("/api/batches/expiring")
    public List<Batch> expiringBatches(
            @RequestParam(defaultValue = "7") int days) {
        return batchService.findExpiring(days);
    }

    @GetMapping("/api/batches/depleted")
    public List<Batch> depletedBatches() {
        return batchService.findDepleted();
    }

    @DeleteMapping("/api/products/{productId}/batches/{batchId}/validate-depletion")
    public ResponseEntity<?> validateDepletion(
            @PathVariable String productId,
            @PathVariable Long batchId) {
        try {
            Batch batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));
            if (!batch.getProduct().getFirestoreId().equals(productId))
                return ResponseEntity.badRequest().body(Map.of("error", "Batch does not belong to product"));
            if (!"depleted".equals(batch.getStatus()))
                return ResponseEntity.badRequest().body(Map.of("error", "Batch is not depleted"));
            batchRepository.delete(batch);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
