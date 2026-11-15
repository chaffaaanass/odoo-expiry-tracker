package io.github.chaffaaanass.expirytracker.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.chaffaaanass.expirytracker.dtos.OdooSessionData;
import io.github.chaffaaanass.expirytracker.services.OdooClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OdooSessionManager {

    @Value("${app.odoo.base-url}")
    private String baseUrl;

    @Value("${app.odoo.database}")
    private String database;

    @Value("${app.odoo.username}")
    private String username;

    @Value("${app.odoo.password}")
    private String password;

    private final OdooClient odooClient;
    private OdooSessionData session;

    @PostConstruct
    public void init() {
        authenticate();
    }

    private void authenticate() {
        log.info("Authenticating with Odoo as '{}'", username);
        this.session = odooClient.authenticate(baseUrl, database, username, password);
        log.info("Odoo session established (uid={})", session.getUid());
    }

    public OdooSessionData getSession() {
        return session;
    }

    // Called by OdooClient when it gets a 401/session expired
    public synchronized void reAuthenticate() {
        log.warn("Odoo session expired — re-authenticating");
        authenticate();
    }
}
