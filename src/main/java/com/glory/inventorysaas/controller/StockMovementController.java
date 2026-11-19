package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.StockPurchaseRequest;
import com.glory.inventorysaas.dto.request.StockSaleRequest;
import com.glory.inventorysaas.dto.request.StockTransferRequest;
import com.glory.inventorysaas.dto.response.AuditResponse;
import com.glory.inventorysaas.dto.response.StockPurchaseResponse;
import com.glory.inventorysaas.dto.response.StockSaleResponse;
import com.glory.inventorysaas.dto.response.StockTransferResponse;
import com.glory.inventorysaas.service.StockService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movement")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StockMovementController {

    private final StockService stockService;

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER')")
    public ResponseEntity<StockTransferResponse> transferStock( @RequestBody StockTransferRequest request) {

        StockTransferResponse response = stockService.transferStock( request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sale")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'OWNER')")
    public ResponseEntity<StockSaleResponse> recordSale ( @RequestBody StockSaleRequest request) {
        StockSaleResponse response = stockService.recordSale(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/purchase/{warehouseId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<StockPurchaseResponse> purchaseStock (@PathVariable UUID warehouseId, @Valid @RequestBody StockPurchaseRequest request) {
        StockPurchaseResponse response = stockService.recordPurchase(warehouseId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/audit")
    public ResponseEntity<List<AuditResponse>> auditLogs () {
        List<AuditResponse> responses = stockService.auditLog();

        return ResponseEntity.ok(responses);
    }
}
