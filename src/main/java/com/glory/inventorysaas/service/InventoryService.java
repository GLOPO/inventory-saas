package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.request.InventoryRequest;
import com.glory.inventorysaas.dto.response.InventoryResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryResponse create(UUID companyId, InventoryRequest request);

    List<InventoryResponse> getAllInventory(UUID companyId);
}
