package com.glory.inventorysaas.repository;

import com.glory.inventorysaas.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
}
