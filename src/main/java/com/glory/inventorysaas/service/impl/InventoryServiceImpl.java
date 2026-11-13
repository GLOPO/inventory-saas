package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.InventoryRequest;
import com.glory.inventorysaas.dto.response.InventoryResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.Inventory;
import com.glory.inventorysaas.entity.Product;
import com.glory.inventorysaas.entity.Warehouse;
import com.glory.inventorysaas.mapper.InventoryMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.InventoryRepository;
import com.glory.inventorysaas.repository.ProductRepository;
import com.glory.inventorysaas.repository.WarehouseRepository;
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

    @Override
    @Transactional
//    @CacheEvict(value = "inventory", key = "#companyId")
    public InventoryResponse create(UUID companyId, InventoryRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company does not exist. ID: " + companyId));

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new RuntimeException("This warehouse does not exists. ID: " + request.warehouseId()));

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new RuntimeException("This product does not exist. ID: " + request.productId()));

        Inventory inventory = Inventory.builder()
                .product(product)
                .warehouse(warehouse)
                .quantity(request.quantity())
                .build();

        return inventoryMapper.toResponse(inventory);
    }

    @Override
    public List<InventoryResponse> getAllInventory(UUID companyId) {
        List<Inventory> inventories = inventoryRepository.findAll();

        return inventories.stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }
}
