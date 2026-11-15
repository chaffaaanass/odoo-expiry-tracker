package io.github.chaffaaanass.expirytracker.services;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import io.github.chaffaaanass.expirytracker.dtos.OdooSessionData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Mirrors OdooService.dart — all XML-RPC calls go through here.
 *
 * Key differences vs Android:
 *  - There is no global singleton; credentials come from the caller
 *    (either the session-scoped OdooSessionData or the scheduled job).
 *  - Cookie handling: the Odoo session_id cookie is managed server-side
 *    and stored in OdooSessionData (stored in the HTTP session).
 */
@Slf4j
@Component
public class OdooClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;  // Odoo-specific mapper

    public OdooClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .build();
    }

    @Value("${app.odoo.connect-timeout-ms:10000}")
    private int connectTimeout;

    private static final DateTimeFormatter ODOO_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Authenticate against Odoo and return a populated OdooSessionData.
     * Equivalent to OdooService.authenticate() in Dart.
     */
    public OdooSessionData authenticate(String baseUrl, String database,
                                        String username, String password) {
        String url = normalise(baseUrl) + "/web/session/authenticate";

        Map<String, Object> body = jsonRpcBody("call", Map.of(
                "db",       database,
                "login",    username,
                "password", password
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        JsonNode result = requireResult(response.getBody());
        int uid = result.path("uid").asInt(0);
        if (uid == 0) {
            throw new OdooException("Authentication failed – check credentials");
        }

        // Extract Odoo session_id cookie (same logic as _extractSessionId in Dart)
        String sessionId = extractSessionId(response.getHeaders());

        return OdooSessionData.builder()
                .baseUrl(normalise(baseUrl))
                .database(database)
                .username(username)
                .password(password)
                .uid(uid)
                .odooSessionId(sessionId)
                .build();
    }

    // ── Products ──────────────────────────────────────────────────────────────

    /**
     * Fetch all Odoo products.
     * Mirrors OdooService.fetchProducts() → searchRead on product.product.
     */
    public List<Map<String, Object>> fetchProducts(OdooSessionData session) {
        return searchRead(session, "product.product",
                List.of(List.of("active", "=", true)),
                List.of("id", "name", "default_code", "categ_id", "uom_id"),
                0, false);
    }

    // ── Purchase Orders ───────────────────────────────────────────────────────

    /**
     * Fetch purchase orders approved/created since [sinceDate], excluding
     * already-seen IDs.  Mirrors OdooService.fetchNewPurchaseOrders().
     */
    public List<Map<String, Object>> fetchPurchaseOrders(
            OdooSessionData session,
            LocalDateTime sinceDate,
            int maxResults) {

        String since = sinceDate.format(ODOO_DATE_FMT);

        List<Object> domain = new ArrayList<>();
        domain.add(List.of("state", "in", List.of("purchase", "done")));
        domain.add("|");
        domain.add(List.of("date_approve", ">=", since));
        domain.add(List.of("date_order",   ">=", since));

        return searchRead(session, "purchase.order", domain,
                List.of("id", "name", "partner_id", "date_approve", "date_order", "state", "order_line"),
                0, maxResults > 0 ? maxResults : 20);
    }

    /** Fetch PO lines for a list of order IDs. */
    public List<Map<String, Object>> fetchPurchaseOrderLines(
            OdooSessionData session, List<Integer> orderLineIds) {

        return searchRead(session, "purchase.order.line",
                List.of(List.of("id", "in", orderLineIds)),
                List.of("id", "order_id", "product_id", "product_qty", "qty_received"),
                0, false);
    }

    // ── XML-RPC helpers ───────────────────────────────────────────────────────
    private List<Map<String, Object>> searchRead(
             OdooSessionData session,
             String model,
             List<Object> domain,
             List<String> fields,
             int offset,
             Object limit) {
        ensureAuthenticated(session);
        try {
            return doSearchRead(session, model, domain, fields, offset, limit);
        } catch (SessionExpiredException e) {
            log.warn("Odoo session expired — re-authenticating and retrying");
            ensureAuthenticated(session, true); // force re-auth
            return doSearchRead(session, model, domain, fields, offset, limit);
        }
    }

    private List<Map<String, Object>> doSearchRead (
            OdooSessionData session,
            String model,
            List<Object> domain,
            List<String> fields,
            int offset,
            Object limit) {

        String url = session.getBaseUrl() + "/web/dataset/call_kw";

        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("model",  model);
        kwargs.put("method", "search_read");
        kwargs.put("args",   List.of(domain));
        kwargs.put("kwargs", Map.of(
                "fields", fields,
                "offset", offset,
                "limit",  limit != null ? limit : false
        ));

        Map<String, Object> body = jsonRpcBody("call", kwargs);

        HttpHeaders headers = sessionHeaders(session);
        JsonNode resp = restTemplate.postForObject(
                url, new HttpEntity<>(body, headers), JsonNode.class);

        JsonNode result = requireResult(resp);

        List<Map<String, Object>> rows = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode node : result) {
                rows.add(mapper.convertValue(node,
                        mapper.getTypeFactory()
                                .constructMapType(Map.class, String.class, Object.class)));
            }
        }
        return rows;
    }

    // ── Re-authentication guard ───────────────────────────────────────────────

    /**
     * Re-authenticate if the Odoo session has expired.
     * Background jobs call this before making any request.
     */
    public void ensureAuthenticated(OdooSessionData session) {
        ensureAuthenticated(session, false);
    }

    public void ensureAuthenticated(OdooSessionData session, boolean force) {
        if (force || session.getOdooSessionId() == null || session.getUid() == null) {
            OdooSessionData fresh = authenticate(
                    session.getBaseUrl(), session.getDatabase(),
                    session.getUsername(), session.getPassword());
            session.setUid(fresh.getUid());
            session.setOdooSessionId(fresh.getOdooSessionId());
        }
    }
    /**
     * Fetches qty_available for a list of Odoo product IDs.
     * Returns e.g. [{ "id": 42, "qty_available": 23.0 }, ...]
     */
    public List<Map<String, Object>> fetchStockQuantities(OdooSessionData session, List<Integer> odooProductIds) {
        if (odooProductIds == null || odooProductIds.isEmpty()) return List.of();

        return searchRead(
                session,
                "product.product",
                List.of(List.of("id", "in", odooProductIds)),
                List.of("id", "qty_available"),
                0,
                false
        );
    }

    // ── Private utilities ─────────────────────────────────────────────────────

    private HttpHeaders sessionHeaders(OdooSessionData session) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (session.getOdooSessionId() != null) {
            h.add(HttpHeaders.COOKIE, "session_id=" + session.getOdooSessionId());
        }
        return h;
    }

    private String extractSessionId(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) return null;
        for (String cookie : cookies) {
            if (cookie.startsWith("session_id=")) {
                return cookie.split(";")[0].substring("session_id=".length());
            }
        }
        return null;
    }

    private Map<String, Object> jsonRpcBody(String method, Object params) {
        return Map.of(
                "jsonrpc", "2.0",
                "id",      1,
                "method",  method,
                "params",  params
        );
    }

    private JsonNode requireResult(JsonNode body) {
        if (body == null) throw new OdooException("Empty response from Odoo");
        if (body.has("error")) {
            String msg = body.path("error").path("message").asText("Odoo error");
            // Odoo returns session-expired errors in the error block
            if (msg.toLowerCase().contains("session") || msg.toLowerCase().contains("expired")) {
                throw new SessionExpiredException(msg);
            }
            throw new OdooException(msg);
        }
        return body.path("result");
    }

    private String normalise(String url) {
        return url.replaceAll("/$", "");
    }

    // ── Exception ─────────────────────────────────────────────────────────────

    public static class OdooException extends RuntimeException {
        public OdooException(String msg) { super(msg); }
    }

    public static class SessionExpiredException extends OdooException {
        public SessionExpiredException(String msg) { super(msg); }
    }
}
