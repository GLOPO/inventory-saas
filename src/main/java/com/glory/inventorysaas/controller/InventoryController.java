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

    @PostMapping("/create")
    public ResponseEntity<InventoryResponse> create( @RequestBody InventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                inventoryService.create( request)
        );
    }

    @GetMapping("/all")
    public ResponseEntity<List<InventoryResponse>> create() {
        return ResponseEntity.ok(
                inventoryService.getAllInventory()
        );
    }
}
