package io.github.chaffaaanass.expirytracker.repositories;

import io.github.chaffaaanass.expirytracker.entities.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findByProduct_FirestoreIdOrderByExpiryDateAsc(String productFirestoreId);

    List<Batch> findByProduct_FirestoreIdAndPurchaseOrderIdOrderByCreatedAtDesc(
            String productFirestoreId, String purchaseOrderId);

    @Query("""
        SELECT b FROM Batch b
        JOIN FETCH b.product p
        WHERE b.expiryDate >= :today
          AND b.expiryDate <= :until
        ORDER BY b.expiryDate ASC
        """)
    List<Batch> findExpiring(@Param("today") LocalDate today,
                             @Param("until") LocalDate until);

    @Query("SELECT b FROM Batch b WHERE b.expiryDate <= :until ORDER BY b.expiryDate ASC")
    List<Batch> findExpiringAndExpired(@Param("until") LocalDate until);

    List<Batch> findAllByStatus(String status);
}
