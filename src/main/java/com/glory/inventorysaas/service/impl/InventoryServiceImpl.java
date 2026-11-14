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
//    @CacheEvict(value = "inventory", key = "#companyId")
    public InventoryResponse create( InventoryRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company does not exist. ID: " + companyId));

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("This warehouse does not exists. ID: " + request.warehouseId()));

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("This product does not exist. ID: " + request.productId()));

        if(inventoryRepository.existsByWarehouseIdAndProductId(request.warehouseId(), request.productId())) {
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
    public List<InventoryResponse> getAllInventory() {
        UUID companyId = securityUtils.getCurrentCompanyId();

        List<Inventory> inventories = inventoryRepository.findByWarehouseCompanyId(companyId);

        return inventories.stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }
}
