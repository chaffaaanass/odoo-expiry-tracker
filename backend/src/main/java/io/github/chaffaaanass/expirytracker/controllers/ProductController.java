package io.github.chaffaaanass.expirytracker.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.github.chaffaaanass.expirytracker.dtos.ProductDtos;
import io.github.chaffaaanass.expirytracker.entities.Product;
import io.github.chaffaaanass.expirytracker.services.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<Product> listProducts() {
        return productService.findAll();
    }

    @PostMapping("/upsert")
    public ResponseEntity<?> upsert(@Valid @RequestBody ProductDtos.UpsertRequest req) {
        productService.upsert(req);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/upsert-bulk")
    public ResponseEntity<?> upsertBulk(
            @RequestBody List<ProductDtos.@Valid UpsertRequest> reqs) {

        if (reqs == null || reqs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Expected non-empty array"));
        }
        int count = productService.upsertBulk(reqs);
        return ResponseEntity.ok(Map.of("ok", true, "count", count));
    }
}
