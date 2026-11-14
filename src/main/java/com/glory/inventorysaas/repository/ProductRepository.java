package com.glory.inventorysaas.repository;

import com.glory.inventorysaas.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsByCompanyIdAndSku(UUID companyId, String sku);
    boolean existsByCompanyIdAndSkuAndIdNot(UUID companyId, String sku, UUID productId);

    List<Product> findByCompanyId(UUID companyId);
}
