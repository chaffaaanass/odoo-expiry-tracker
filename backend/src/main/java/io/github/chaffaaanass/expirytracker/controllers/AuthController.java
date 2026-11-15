package io.github.chaffaaanass.expirytracker.controllers;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.chaffaaanass.expirytracker.dtos.AuthDtos;
import io.github.chaffaaanass.expirytracker.dtos.OdooSessionData;
import io.github.chaffaaanass.expirytracker.services.OdooClient;
import io.github.chaffaaanass.expirytracker.services.OdooSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String SESSION_KEY = "odoo_session";

    // ── Status (replaces SharedPreferences.getBool('is_logged_in')) ───────────

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpSession session) {
        log.info("Session ID: {}, attributes: {}", session.getId(),
                Collections.list(session.getAttributeNames()));
        OdooSessionData data = (OdooSessionData) session.getAttribute(SESSION_KEY);
        if (data == null) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "uid",           data.getUid(),
                "username",      data.getUsername(),
                "baseUrl",       data.getBaseUrl()
        ));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();   // removes SESSION cookie + deletes from DB
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
