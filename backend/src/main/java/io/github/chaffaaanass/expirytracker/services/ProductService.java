package io.github.chaffaaanass.expirytracker.services;

import lombok.RequiredArgsConstructor;
import io.github.chaffaaanass.expirytracker.dtos.ProductDtos;
import io.github.chaffaaanass.expirytracker.entities.Product;
import io.github.chaffaaanass.expirytracker.repositories.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public void upsert(ProductDtos.UpsertRequest req) {
        Product p = productRepository.findById(req.getFirestoreId())
                .orElse(Product.builder()
                        .firestoreId(req.getFirestoreId())
                        .build());

        p.setOdooId(req.getOdooId());
        p.setName(req.getName());
        p.setCode(req.getCode());
        p.setCategory(req.getCategory());
        p.setUnit(req.getUnit());
        p.setSource(req.getSource());

        productRepository.save(p);
    }

    @Transactional
    public int upsertBulk(List<ProductDtos.UpsertRequest> reqs) {
        reqs.forEach(this::upsert);
        return reqs.size();
    }
}
