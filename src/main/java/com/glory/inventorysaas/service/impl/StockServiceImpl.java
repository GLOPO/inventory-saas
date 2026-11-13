package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.StockTransferRequest;
import com.glory.inventorysaas.dto.response.StockTransferResponse;
import com.glory.inventorysaas.entity.Inventory;
import com.glory.inventorysaas.entity.Product;
import com.glory.inventorysaas.entity.StockMovement;
import com.glory.inventorysaas.entity.Warehouse;
import com.glory.inventorysaas.entity.enums.MovementType;
import com.glory.inventorysaas.mapper.StockMovementMapper;
import com.glory.inventorysaas.repository.InventoryRepository;
import com.glory.inventorysaas.repository.ProductRepository;
import com.glory.inventorysaas.repository.StockMovementRepository;
import com.glory.inventorysaas.repository.WarehouseRepository;
import com.glory.inventorysaas.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockMovementRepository stockRepository;
    private final StockMovementMapper stockMovementMapper;

    @Override
    @Transactional
    @CacheEvict(value = "transferStock", key = "#companyId")
    public StockTransferResponse transferStock(UUID companyId, StockTransferRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new RuntimeException("Product does not exists. ID: " + request.productId()));

        Warehouse sourceWarehouse = warehouseRepository.findById(request.sourceWarehouseId())
                .orElseThrow(() -> new RuntimeException("Source warehouse not found. ID: " + request.sourceWarehouseId()));

        Inventory sourceInventory = inventoryRepository.findByWarehouseIdAndProductId(request.sourceWarehouseId(), request.productId())
                .orElseThrow(() -> new RuntimeException("No stock record found in stock warehouse. ID: " + request.sourceWarehouseId()));


        if (sourceInventory.getQuantity() < request.quantity()) {
            throw new IllegalArgumentException("Insufficient stock in Source warehouse. " +
                    "Available: " + sourceInventory.getQuantity() +
                    "Requested: " + request.quantity());
        }

        Warehouse destinationWarehouse = warehouseRepository.findById(request.destinationWarehouseId())
                .orElseThrow(() -> new RuntimeException("Destination warehouse not found. ID: " + request.destinationWarehouseId()));

        sourceInventory.setQuantity(sourceInventory.getQuantity() - request.quantity());
        inventoryRepository.save(sourceInventory);

        Inventory destinationInventory = inventoryRepository.findByWarehouseIdAndProductId(request.destinationWarehouseId(), request.productId())
                .orElseGet(() -> {
                    return Inventory.builder()
                            .product(product)
                            .quantity(0)
                            .warehouse(destinationWarehouse)
                            .build();
                });

        destinationInventory.setQuantity(destinationInventory.getQuantity() + request.quantity());
        inventoryRepository.save(destinationInventory);

        StockMovement movement = StockMovement.builder()
                .movementType(MovementType.TRANSFER)
                .destinationWarehouse(destinationWarehouse)
                .sourceWarehouse(sourceWarehouse)
                .product(product)
                .quantity(request.quantity())
                .build();

        StockMovement saved = stockRepository.save(movement);

        return stockMovementMapper.toResponse(saved);
    }
}
