package io.github.chaffaaanass.expirytracker.repositories;

import io.github.chaffaaanass.expirytracker.entities.PoTracker;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoTrackerRepository extends JpaRepository<PoTracker, Integer> {
}