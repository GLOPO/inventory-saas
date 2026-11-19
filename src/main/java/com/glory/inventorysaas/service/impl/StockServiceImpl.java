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
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.mapper.StockMovementMapper;
import com.glory.inventorysaas.repository.InventoryRepository;
import com.glory.inventorysaas.repository.ProductRepository;
import com.glory.inventorysaas.repository.StockMovementRepository;
import com.glory.inventorysaas.repository.WarehouseRepository;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.StockService;
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
public class StockServiceImpl implements StockService {
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockMovementRepository stockRepository;
    private final StockMovementMapper stockMovementMapper;
    private final SecurityUtils securityUtils;

    private void validateAssignedWarehouse(UUID warehouseId) {
        UUID assignedWarehouse = securityUtils.getCurrentWarehouseId();

        if (assignedWarehouse == null || !assignedWarehouse.equals(warehouseId)) {
            throw new AccessDeniedException("Access Denied. You can only perform this operation in your assigned warehouse");
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"inventory", "audit-log"}, key = "@securityUtils.getCurrentCompanyCacheKey()")
    public StockTransferResponse transferStock( StockTransferRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        if (request.destinationWarehouseId().equals(warehouseId)) {
            throw new IllegalArgumentException("You cannot move stock to the same warehouse." +
                    "Source warehouse ID: " + warehouseId +
                    "Destination warehouse ID: " + request.destinationWarehouseId());
        }
        Product product = productRepository.findByIdAndCompanyId(request.productId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product does not exists. ID: " + request.productId()));

        Warehouse sourceWarehouse = warehouseRepository.findByIdAndCompanyId(warehouseId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Source warehouse not found. ID: " + warehouseId));

        Inventory sourceInventory = inventoryRepository.findByWarehouseIdAndProductId(warehouseId, request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("No stock record found in stock warehouse. ID: " + warehouseId));


        if (sourceInventory.getQuantity() < request.quantity()) {
            throw new IllegalArgumentException("Insufficient stock in Source warehouse. " +
                    "Available: " + sourceInventory.getQuantity() +
                    "Requested: " + request.quantity());
        }

        Warehouse destinationWarehouse = warehouseRepository.findByIdAndCompanyId(request.destinationWarehouseId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse not found. ID: " + request.destinationWarehouseId()));

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
    @CacheEvict(value = {"inventory", "audit-log"}, key = "@securityUtils.getCurrentCompanyCacheKey()")
    public StockSaleResponse recordSale( StockSaleRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        Product product = productRepository.findByIdAndCompanyId(request.productId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("This product does not exists. ID: " + request.productId()));

        Warehouse sourceWarehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("This warehouse does not exist. ID: " + warehouseId));

        Inventory inventory = inventoryRepository.findByWarehouseIdAndProductId(warehouseId, request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("No stock record found in source warehouse. ID: " + warehouseId));

        if (inventory.getQuantity() < request.quantity()) {
            throw new IllegalArgumentException("Insufficient Stock. " +
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
    @CacheEvict(value = {"inventory", "audit-log"}, key = "@securityUtils.getCurrentCompanyCacheKey()")
    public StockPurchaseResponse recordPurchase(UUID warehouseId, StockPurchaseRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        Product product = productRepository.findByIdAndCompanyId(request.productId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product does not exist. ID: " + request.productId()));

        Warehouse destinationWarehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse does not exist. ID: " + warehouseId));

        Inventory inventory = inventoryRepository.findByWarehouseIdAndProductId(warehouseId, request.productId())
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
    @Transactional(readOnly = true)
    @Cacheable(
            value = "audit-log",
            key = "@securityUtils.getCurrentCompanyCacheKey()"
    )
    public List<AuditResponse> auditLog() {
        UUID companyId = securityUtils.getCurrentCompanyId();

        List<StockMovement> responses =
                stockRepository.findByProductCompanyIdOrderByCreatedAtDesc(companyId);

        return responses.stream()
                .map(stockMovementMapper::toAuditResponse)
                .toList();
    }
}
