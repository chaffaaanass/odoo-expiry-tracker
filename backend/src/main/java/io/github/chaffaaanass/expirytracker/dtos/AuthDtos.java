package io.github.chaffaaanass.expirytracker.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


public class AuthDtos {

    @Data
    public static class LoginRequest {
        @NotBlank String baseUrl;
        @NotBlank String database;
        @NotBlank String username;
        @NotBlank String password;
    }

    @Data
    public static class LoginResponse {
        private boolean ok;
        private int uid;
        private String username;
        private String baseUrl;

        public LoginResponse(int uid, String username, String baseUrl) {
            this.ok = true;
            this.uid = uid;
            this.username = username;
            this.baseUrl = baseUrl;
        }
    }
}
