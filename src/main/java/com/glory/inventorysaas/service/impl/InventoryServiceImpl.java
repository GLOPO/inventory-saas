package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.InventoryRequest;
import com.glory.inventorysaas.dto.response.InventoryResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.Inventory;
import com.glory.inventorysaas.entity.Product;
import com.glory.inventorysaas.entity.Warehouse;
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.mapper.InventoryMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.InventoryRepository;
import com.glory.inventorysaas.repository.ProductRepository;
import com.glory.inventorysaas.repository.WarehouseRepository;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final CompanyRepository companyRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "@securityUtils.getCurrentCompanyCacheKey()")
    public InventoryResponse create( InventoryRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        if(warehouseId == null) {
            throw new AccessDeniedException("Access Denied. You are not assigned to a warehouse");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company does not exist. ID: " + companyId));

        Warehouse warehouse = warehouseRepository.findByIdAndCompanyId(warehouseId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("This warehouse does not exists. ID: " + warehouseId));

        Product product = productRepository.findByIdAndCompanyId(request.productId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("This product does not exist. ID: " + request.productId()));

        if(inventoryRepository.existsByWarehouseIdAndProductId(warehouseId, request.productId())) {
            throw new IllegalArgumentException("Inventory already exists for this product in this warehouse");
        }

        Inventory inventory = Inventory.builder()
                .product(product)
                .warehouse(warehouse)
                .quantity(request.quantity())
                .build();

        Inventory saved = inventoryRepository.save(inventory);

        return inventoryMapper.toResponse(saved);
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "inventory", key = "@securityUtils.getCurrentCompanyCacheKey()")
    public List<InventoryResponse> getAllInventory() {

        UUID companyId = securityUtils.getCurrentCompanyId();

        List<Inventory> inventories =
                inventoryRepository.findByWarehouse_Company_Id(companyId);

        return inventories.stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

}
