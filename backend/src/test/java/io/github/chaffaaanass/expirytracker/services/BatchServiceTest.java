package io.github.chaffaaanass.expirytracker.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import io.github.chaffaaanass.expirytracker.dtos.BatchDtos;
import io.github.chaffaaanass.expirytracker.entities.Batch;
import io.github.chaffaaanass.expirytracker.entities.Product;
import io.github.chaffaaanass.expirytracker.repositories.BatchRepository;
import io.github.chaffaaanass.expirytracker.repositories.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    private static final String PRODUCT_ID = "p1";
    private static final Long BATCH_ID = 1L;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private BatchService batchService;

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesAnActiveBatchLinkedToItsProduct() {
        Product product = product(PRODUCT_ID);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchDtos.CreateRequest req = new BatchDtos.CreateRequest();
        req.setBatchNumber("LOT-1");
        req.setExpiryDate(LocalDate.of(2027, 1, 31));
        req.setQuantity(12.5);
        req.setPurchaseOrderId("PO-42");

        Batch saved = batchService.create(PRODUCT_ID, req);

        assertThat(saved.getProduct()).isSameAs(product);
        assertThat(saved.getBatchNumber()).isEqualTo("LOT-1");
        assertThat(saved.getExpiryDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(saved.getQuantity()).isEqualTo(12.5);
        assertThat(saved.getPurchaseOrderId()).isEqualTo("PO-42");
        assertThat(saved.getStatus()).isEqualTo("active");
    }

    @Test
    void create_rejectsAnUnknownProduct() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchService.create("missing", new BatchDtos.CreateRequest()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("missing");

        verify(batchRepository, never()).save(any(Batch.class));
    }

    // ── patch ─────────────────────────────────────────────────────────────────

    @Test
    void patch_acceptsSnakeCaseAndCamelCaseFieldNames() {
        Batch batch = batchOf(PRODUCT_ID);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));

        Map<String, Object> fields = Map.of(
                "batch_number", "LOT-2",
                "expiryDate", "2027-03-01",
                "quantity", 5);

        batchService.patch(PRODUCT_ID, BATCH_ID, fields);

        assertThat(batch.getBatchNumber()).isEqualTo("LOT-2");
        assertThat(batch.getExpiryDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(batch.getQuantity()).isEqualTo(5.0);
        verify(batchRepository).save(batch);
    }

    @Test
    void patch_ignoresUnknownFields() {
        Batch batch = batchOf(PRODUCT_ID);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));

        Map<String, Object> fields = Map.of("not_a_field", "whatever");

        batchService.patch(PRODUCT_ID, BATCH_ID, fields);

        assertThat(batch.getBatchNumber()).isEqualTo("LOT-1");
        assertThat(batch.getQuantity()).isEqualTo(10.0);
        verify(batchRepository).save(batch);
    }

    @Test
    void patch_rejectsAnInvalidDateAndSavesNothing() {
        Batch batch = batchOf(PRODUCT_ID);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));

        Map<String, Object> fields = Map.of("expiry_date", "not-a-date");

        assertThatThrownBy(() -> batchService.patch(PRODUCT_ID, BATCH_ID, fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiry_date");

        verify(batchRepository, never()).save(any(Batch.class));
    }

    @Test
    void patch_rejectsABatchThatBelongsToAnotherProduct() {
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batchOf(PRODUCT_ID)));

        Map<String, Object> fields = Map.of("notes", "moved");

        assertThatThrownBy(() -> batchService.patch("another-product", BATCH_ID, fields))
                .isInstanceOf(IllegalArgumentException.class);

        verify(batchRepository, never()).save(any(Batch.class));
    }

    @Test
    void patch_rejectsAnUnknownBatch() {
        when(batchRepository.findById(99L)).thenReturn(Optional.empty());

        Map<String, Object> fields = Map.of("notes", "x");

        assertThatThrownBy(() -> batchService.patch(PRODUCT_ID, 99L, fields))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesTheBatchOfTheGivenProduct() {
        Batch batch = batchOf(PRODUCT_ID);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));

        batchService.delete(PRODUCT_ID, BATCH_ID);

        verify(batchRepository).delete(batch);
    }

    @Test
    void delete_rejectsABatchThatBelongsToAnotherProduct() {
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batchOf(PRODUCT_ID)));

        assertThatThrownBy(() -> batchService.delete("another-product", BATCH_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verify(batchRepository, never()).delete(any(Batch.class));
    }

    // ── expiry window ─────────────────────────────────────────────────────────

    @Test
    void findExpiring_asksForEverythingExpiringUpToTodayPlusTheWindow() {
        Batch soon = batchOf(PRODUCT_ID);
        when(batchRepository.findExpiringAndExpired(any(LocalDate.class))).thenReturn(List.of(soon));

        LocalDate before = LocalDate.now();
        List<Batch> result = batchService.findExpiring(30);
        LocalDate after = LocalDate.now();

        ArgumentCaptor<LocalDate> until = ArgumentCaptor.forClass(LocalDate.class);
        verify(batchRepository).findExpiringAndExpired(until.capture());

        // "before" and "after" only differ if the test runs across midnight.
        assertThat(until.getValue()).isBetween(before.plusDays(30), after.plusDays(30));
        assertThat(result).containsExactly(soon);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static Product product(String id) {
        return Product.builder().firestoreId(id).name("Milk 1L").build();
    }

    private static Batch batchOf(String productId) {
        return Batch.builder()
                .id(BATCH_ID)
                .product(product(productId))
                .batchNumber("LOT-1")
                .expiryDate(LocalDate.of(2027, 1, 31))
                .quantity(10.0)
                .build();
    }
}
