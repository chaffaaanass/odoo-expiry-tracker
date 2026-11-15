package io.github.chaffaaanass.expirytracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Value("${app.odoo.connect-timeout-ms:10000}")
    private int connectTimeout;

    @Value("${app.odoo.read-timeout-ms:30000}")
    private int readTimeout;

    /**
     * RestTemplate used by OdooClient to call the Odoo instance.
     * Timeouts match the Flutter app's http.Client defaults.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .readTimeout(Duration.ofMillis(readTimeout))
                .build();
    }
}

