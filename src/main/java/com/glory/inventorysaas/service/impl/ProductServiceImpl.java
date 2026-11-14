package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.ProductRequest;
import com.glory.inventorysaas.dto.response.ProductResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.Product;
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.mapper.ProductMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.ProductRepository;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "@securityUtils.getCurrentCompanyCacheKey()")
    public ProductResponse create( ProductRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with ID: " + companyId));

        if (productRepository.existsByCompanyIdAndSku(companyId, request.sku())) {
            throw new IllegalArgumentException("This product already exists in your company. SKU: " + request.sku());
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
    @Cacheable(
            value = "products",
            key = "@securityUtils.getCurrentCompanyCacheKey()"
    )
    public List<ProductResponse> getAllProducts() {
        UUID companyId = securityUtils.getCurrentCompanyId();

        List<Product> products = productRepository.findByCompanyId(companyId);

        return products.stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "@securityUtils.getCurrentCompanyCacheKey()")
    public ProductResponse update( UUID productId, ProductRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product does not exist. ID: " + productId));

        if (!product.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException(("You are not authorized to edit this product"));
        }

        if(!Objects.equals(product.getSku(), request.sku())) {
            boolean skuExists = productRepository.existsByCompanyIdAndSkuAndIdNot(companyId, request.sku(), productId);

            if (skuExists) {
                throw new IllegalArgumentException("Another product in your company already exists with this sku: " + request.sku());
            }
        }

        product.setName(request.name());
        product.setSku(request.sku());

        Product saved = productRepository.save(product);

        return productMapper.toResponse(saved);
    }
}
