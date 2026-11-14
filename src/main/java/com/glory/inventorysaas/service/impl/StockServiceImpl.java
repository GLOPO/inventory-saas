package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.response.*;
import com.glory.inventorysaas.dto.request.StockPurchaseRequest;
import com.glory.inventorysaas.dto.request.StockSaleRequest;
import com.glory.inventorysaas.dto.request.StockTransferRequest;
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

import java.util.List;
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
    @CacheEvict(value = "inventory", key = "#companyId")
    public StockTransferResponse transferStock(UUID companyId, StockTransferRequest request) {
        if (request.destinationWarehouseId().equals(request.sourceWarehouseId())) {
            throw new IllegalArgumentException("You cannot move stock to the same warehouse." +
                    "Source warehouse ID: " + request.sourceWarehouseId() +
                    "Destination warehouse ID: " + request.destinationWarehouseId());
        }
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

        return stockMovementMapper.toTransferResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#companyId")
    public StockSaleResponse recordSale(UUID companyId, StockSaleRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new RuntimeException("This product does not exists. ID: " + request.productId()));

        Warehouse sourceWarehouse = warehouseRepository.findById(request.sourceWarehouseId())
                .orElseThrow(() -> new RuntimeException("Tis warehouse does not exist. ID: " + request.sourceWarehouseId()));

        Inventory inventory = inventoryRepository.findByWarehouseIdAndProductId(request.sourceWarehouseId(), request.productId())
                .orElseThrow(() -> new RuntimeException("No stock record found in source warehouse. ID: " + request.sourceWarehouseId()));

        if (inventory.getQuantity() < request.quantity()) {
            throw new IllegalArgumentException("Insufficient Stock." +
                    "Available: " + inventory.getQuantity() +
                    "Requested: " + request.quantity());
        }

        inventory.setQuantity(inventory.getQuantity() - request.quantity());
        inventoryRepository.save(inventory);

        StockMovement movement = StockMovement.builder()
                .quantity(request.quantity())
                .product(product)
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(null)
                .movementType(MovementType.SALE)
                .build();

        return stockMovementMapper.toSaleResponse(
                stockRepository.save(movement)
        );
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#companyId")
    public StockPurchaseResponse recordPurchase(UUID companyId, StockPurchaseRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new RuntimeException("Product does not exist. ID: " + request.productId()));

        Warehouse destinationWarehouse = warehouseRepository.findById(request.destinationWarehouseId())
                .orElseThrow(() -> new RuntimeException("Warehouse does not exist. ID: " + request.destinationWarehouseId()));

        Inventory inventory = inventoryRepository.findByWarehouseIdAndProductId(request.destinationWarehouseId(), request.productId())
                .orElseGet(() -> {
                    return Inventory.builder()
                            .product(product)
                            .quantity(0)
                            .warehouse(destinationWarehouse)
                            .build();
                });

        inventory.setQuantity(inventory.getQuantity() + request.quantity());
        Inventory saved = inventoryRepository.save(inventory);

        StockMovement movement = StockMovement.builder()
                .movementType(MovementType.PURCHASE)
                .destinationWarehouse(destinationWarehouse)
                .sourceWarehouse(null)
                .product(product)
                .quantity(request.quantity())
                .build();

        return stockMovementMapper.toPurchaseResponse(
                stockRepository.save(movement)
        );
    }

    @Override
    public List<AuditResponse> auditLog(UUID companyId) {
        List<StockMovement> responses = stockRepository.findByProductCompanyIdOrderByCreatedAtDesc(companyId);

        return responses.stream()
                .map(stockMovementMapper::toAuditResponse)
                .toList();
    }
}
