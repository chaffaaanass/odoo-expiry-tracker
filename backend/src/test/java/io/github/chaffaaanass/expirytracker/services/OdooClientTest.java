package io.github.chaffaaanass.expirytracker.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.chaffaaanass.expirytracker.dtos.OdooSessionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Covers the Odoo login call: what the client sends, and how it reads Odoo's
 * three possible answers (session, refused login, error block).
 */
@ExtendWith(MockitoExtension.class)
class OdooClientTest {

    private static final String AUTH_URL = "https://odoo.example.com/web/session/authenticate";

    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private RestTemplate restTemplate;

    private OdooClient odooClient;

    @BeforeEach
    void setUp() {
        odooClient = new OdooClient(restTemplate);
    }

    @Test
    void authenticate_returnsASessionWithTheUidAndTheSessionCookie() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, "session_id=abc123; Path=/; HttpOnly");
        odooAnswers(headers, "{\"jsonrpc\":\"2.0\",\"result\":{\"uid\":7}}");

        // The trailing slash on the base URL must not produce a double slash.
        OdooSessionData session =
                odooClient.authenticate("https://odoo.example.com/", "prod", "bot@example.com", "secret");

        assertThat(session.getUid()).isEqualTo(7);
        assertThat(session.getOdooSessionId()).isEqualTo("abc123");
        assertThat(session.getBaseUrl()).isEqualTo("https://odoo.example.com");
        assertThat(session.getDatabase()).isEqualTo("prod");
        assertThat(session.getUsername()).isEqualTo("bot@example.com");
    }

    @Test
    void authenticate_failsWhenOdooReturnsNoUid() {
        odooAnswers(new HttpHeaders(), "{\"jsonrpc\":\"2.0\",\"result\":{}}");

        assertThatThrownBy(() ->
                odooClient.authenticate("https://odoo.example.com", "prod", "bot@example.com", "wrong"))
                .isInstanceOf(OdooClient.OdooException.class)
                .hasMessageContaining("Authentication failed");
    }

    @Test
    void authenticate_surfacesOdooErrorMessages() {
        odooAnswers(new HttpHeaders(), "{\"error\":{\"message\":\"Access Denied\"}}");

        assertThatThrownBy(() ->
                odooClient.authenticate("https://odoo.example.com", "prod", "bot@example.com", "secret"))
                .isInstanceOf(OdooClient.OdooException.class)
                .isNotInstanceOf(OdooClient.SessionExpiredException.class)
                .hasMessage("Access Denied");
    }

    @Test
    void authenticate_reportsAnExpiredSessionAsItsOwnException() {
        odooAnswers(new HttpHeaders(), "{\"error\":{\"message\":\"Session expired\"}}");

        assertThatThrownBy(() ->
                odooClient.authenticate("https://odoo.example.com", "prod", "bot@example.com", "secret"))
                .isInstanceOf(OdooClient.SessionExpiredException.class);
    }

    private void odooAnswers(HttpHeaders headers, String body) {
        JsonNode node = json.readTree(body);
        when(restTemplate.postForEntity(eq(AUTH_URL), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok().headers(headers).body(node));
    }
}
