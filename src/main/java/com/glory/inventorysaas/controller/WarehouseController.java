package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.WarehouseRequest;
import com.glory.inventorysaas.dto.response.WarehouseResponse;
import com.glory.inventorysaas.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping("/company/{companyId}")
    public ResponseEntity<WarehouseResponse> create(@PathVariable UUID companyId, @RequestBody WarehouseRequest request) {
        WarehouseResponse response = warehouseService.createWarehouse(companyId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
