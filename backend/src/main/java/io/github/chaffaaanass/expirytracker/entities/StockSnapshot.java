package io.github.chaffaaanass.expirytracker.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "stock_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockSnapshot {

    @Id
    @Column(name = "odoo_product_id")
    private Integer odooProductId;

    @Column(name = "last_known_qty", nullable = false)
    private Double lastKnownQty;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public StockSnapshot(Integer odooProductId, Double lastKnownQty) {
        this.odooProductId = odooProductId;
        this.lastKnownQty = lastKnownQty;
    }
}