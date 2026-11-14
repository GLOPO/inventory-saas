package com.glory.inventorysaas.repository;

import com.glory.inventorysaas.entity.Inventory;
import com.glory.inventorysaas.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    boolean existsByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    List<Inventory> findByWarehouseCompanyId (UUID companyId);
}
