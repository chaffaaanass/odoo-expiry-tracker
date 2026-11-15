package io.github.chaffaaanass.expirytracker.controllers;

import lombok.RequiredArgsConstructor;
import io.github.chaffaaanass.expirytracker.services.PoTrackerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Mirrors the Node.js PO tracker routes:
 *
 *   GET  /api/po-tracker            → list of seen Odoo PO ids
 *   POST /api/po-tracker/mark-seen  → body: { ids: [1,2,3] }
 */
@RestController
@RequestMapping("/api/po-tracker")
@RequiredArgsConstructor
public class PoTrackerController {

    private final PoTrackerService poTrackerService;

    @GetMapping
    public List<Integer> getTracked() {
        return poTrackerService.getTrackedIds();
    }

    @PostMapping("/mark-seen")
    public ResponseEntity<?> markSeen(@RequestBody Map<String, List<Integer>> body) {
        List<Integer> ids = body.get("ids");
        if (ids == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Expected { ids: [] }"));
        }
        poTrackerService.markSeen(ids);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
