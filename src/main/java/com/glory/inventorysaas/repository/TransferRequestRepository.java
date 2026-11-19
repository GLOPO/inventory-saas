package com.glory.inventorysaas.repository;

import com.glory.inventorysaas.entity.TransferRequest;
import com.glory.inventorysaas.entity.enums.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, UUID> {

    Optional<TransferRequest> findByIdAndCompanyId(UUID id, UUID companyId);

    List<TransferRequest> findBySourceWarehouseIdAndStatusOrderByCreatedAtDesc(UUID sourceWarehouseId, TransferStatus status);

    List<TransferRequest> findByDestinationWarehouseIdOrderByCreatedAtDesc(UUID destinationWarehouseId);
}
