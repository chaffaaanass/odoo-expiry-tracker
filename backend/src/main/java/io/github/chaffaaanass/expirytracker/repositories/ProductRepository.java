package io.github.chaffaaanass.expirytracker.repositories;

import io.github.chaffaaanass.expirytracker.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findAllByOrderByNameAsc();
    @Query("SELECT DISTINCT p FROM Product p JOIN p.batches b WHERE p.odooId IS NOT NULL AND b.status = 'active'")
    List<Product> findAllWithOdooIdAndBatches();
}