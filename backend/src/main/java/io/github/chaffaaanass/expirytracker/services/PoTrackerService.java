package io.github.chaffaaanass.expirytracker.services;

import lombok.RequiredArgsConstructor;
import io.github.chaffaaanass.expirytracker.entities.PoTracker;
import io.github.chaffaaanass.expirytracker.repositories.PoTrackerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PoTrackerService {

    private final PoTrackerRepository poTrackerRepository;

    @Transactional(readOnly = true)
    public List<Integer> getTrackedIds() {
        return poTrackerRepository.findAll()
                .stream()
                .map(PoTracker::getOdooId)
                .collect(Collectors.toList());
    }

    /**
     * INSERT … ON CONFLICT DO NOTHING — mirrors Node.js /po-tracker/mark-seen.
     */
    @Transactional
    public void markSeen(List<Integer> ids) {
        for (Integer id : ids) {
            if (!poTrackerRepository.existsById(id)) {
                poTrackerRepository.save(new PoTracker(id));
            }
        }
    }
}
