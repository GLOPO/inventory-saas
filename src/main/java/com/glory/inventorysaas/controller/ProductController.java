package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.ProductRequest;
import com.glory.inventorysaas.dto.response.ProductResponse;
import com.glory.inventorysaas.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping("/create/{companyId}")
    public ResponseEntity<ProductResponse> create(@PathVariable UUID companyId, @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(companyId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/all/{companyId}")
    public ResponseEntity<List<ProductResponse>> getAll(@PathVariable UUID companyId) {

        return ResponseEntity.ok(
                productService.getAllProducts(companyId)
        );
    }

    @PutMapping("/update/{companyId}/{productId}")
    public ResponseEntity<ProductResponse> update(@PathVariable UUID companyId, @PathVariable UUID productId, @Valid @RequestBody ProductRequest request) {
       ProductResponse response = productService.update(companyId, productId, request);

       return ResponseEntity.ok(response);
    }
}
