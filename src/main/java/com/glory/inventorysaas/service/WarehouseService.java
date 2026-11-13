package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.request.WarehouseRequest;
import com.glory.inventorysaas.dto.response.WarehouseResponse;

import java.util.List;
import java.util.UUID;

public interface WarehouseService {

    WarehouseResponse createWarehouse(UUID companyId, WarehouseRequest request);

    List<WarehouseResponse> getAllWarehouse(UUID companyId);
}
