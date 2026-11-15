package io.github.chaffaaanass.expirytracker.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "po_tracker")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PoTracker {

    @Id
    @Column(name = "odoo_id")
    private Integer odooId;
}
