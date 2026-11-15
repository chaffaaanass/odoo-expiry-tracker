package io.github.chaffaaanass.expirytracker.repositories;

import io.github.chaffaaanass.expirytracker.entities.StockSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockSnapshotRepository extends JpaRepository<StockSnapshot, Integer> {
}