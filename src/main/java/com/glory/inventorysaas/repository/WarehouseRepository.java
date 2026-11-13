package com.glory.inventorysaas.repository;

import com.glory.inventorysaas.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findByCompanyId(UUID id);
}
