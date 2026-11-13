package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.ProductRequest;
import com.glory.inventorysaas.dto.response.ProductResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.Product;
import com.glory.inventorysaas.mapper.ProductMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.ProductRepository;
import com.glory.inventorysaas.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#companyId")
    public ProductResponse create(UUID companyId, ProductRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found with ID: " + companyId));

        if (productRepository.existsByCompanyIdAndSku(companyId, request.sku())) {
            throw new RuntimeException("This product already exists in your company. SKU: " + request.sku());
        }

        Product product = Product.builder()
                .company(company)
                .name(request.name())
                .sku(request.sku())
                .build();

        Product saved = productRepository.save(product);

        return productMapper.toResponse(saved);
    }

    @Override
    @Cacheable(value = "products", key = "#companyId")
    public List<ProductResponse> getAllProducts(UUID companyId) {
        List<Product> products = productRepository.findByCompanyId(companyId);

        return products.stream()
                .map(productMapper::toResponse)
                .toList();
    }
}
