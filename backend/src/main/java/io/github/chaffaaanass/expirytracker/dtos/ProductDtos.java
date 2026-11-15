package io.github.chaffaaanass.expirytracker.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class ProductDtos {
    @Data
    public static class UpsertRequest {
        @NotBlank String firestoreId;   // maps to firestore_id column
        Integer odooId;
        @NotBlank String name;
        String code;
        String category;
        String unit;
        String source;
    }
}
