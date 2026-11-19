package com.glory.inventorysaas.repository;

import com.glory.inventorysaas.entity.CustomerOrder;
import com.glory.inventorysaas.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    List<CustomerOrder> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<CustomerOrder> findByIdAndCompanyId(UUID orderId, UUID companyId);

    List<CustomerOrder> findByWarehouseIdAndStatusOrderByCreatedAtDesc(UUID warehouseId, OrderStatus status);
}
