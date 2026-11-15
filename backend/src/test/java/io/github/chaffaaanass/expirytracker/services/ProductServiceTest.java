package io.github.chaffaaanass.expirytracker.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import io.github.chaffaaanass.expirytracker.dtos.ProductDtos;
import io.github.chaffaaanass.expirytracker.entities.Product;
import io.github.chaffaaanass.expirytracker.repositories.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void upsert_createsTheProductWhenItDoesNotExist() {
        when(productRepository.findById("p1")).thenReturn(Optional.empty());

        productService.upsert(request("p1", 101, "Milk 1L"));

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getFirestoreId()).isEqualTo("p1");
        assertThat(saved.getValue().getOdooId()).isEqualTo(101);
        assertThat(saved.getValue().getName()).isEqualTo("Milk 1L");
    }

    @Test
    void upsert_updatesTheExistingProductInPlace() {
        Product existing = Product.builder().firestoreId("p1").odooId(101).name("Old name").build();
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));

        productService.upsert(request("p1", 101, "New name"));

        verify(productRepository).save(existing);
        assertThat(existing.getName()).isEqualTo("New name");
    }

    @Test
    void upsertBulk_savesEveryProductAndReturnsTheCount() {
        when(productRepository.findById(anyString())).thenReturn(Optional.empty());

        int count = productService.upsertBulk(List.of(
                request("p1", 101, "Milk 1L"),
                request("p2", 102, "Butter 250g")));

        assertThat(count).isEqualTo(2);
        verify(productRepository, times(2)).save(any(Product.class));
    }

    private static ProductDtos.UpsertRequest request(String id, Integer odooId, String name) {
        ProductDtos.UpsertRequest req = new ProductDtos.UpsertRequest();
        req.setFirestoreId(id);
        req.setOdooId(odooId);
        req.setName(name);
        return req;
    }
}
