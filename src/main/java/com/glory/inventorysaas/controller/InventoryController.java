package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.InventoryRequest;
import com.glory.inventorysaas.dto.response.InventoryResponse;
import com.glory.inventorysaas.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @PostMapping("/create/{companyId}")
    public ResponseEntity<InventoryResponse> create(@PathVariable UUID companyId, @RequestBody InventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                inventoryService.create(companyId, request)
        );
    }

    @GetMapping("/all/{companyId}")
    public ResponseEntity<List<InventoryResponse>> create(@PathVariable UUID companyId) {
        return ResponseEntity.ok(
                inventoryService.getAllInventory(companyId)
        );
    }
}
