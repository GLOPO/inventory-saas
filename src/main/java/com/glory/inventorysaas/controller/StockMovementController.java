package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.StockPurchaseRequest;
import com.glory.inventorysaas.dto.request.StockSaleRequest;
import com.glory.inventorysaas.dto.request.StockTransferRequest;
import com.glory.inventorysaas.dto.response.AuditResponse;
import com.glory.inventorysaas.dto.response.StockPurchaseResponse;
import com.glory.inventorysaas.dto.response.StockSaleResponse;
import com.glory.inventorysaas.dto.response.StockTransferResponse;
import com.glory.inventorysaas.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movement")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockService stockService;

    @PostMapping("/transfer/{companyId}")
    public ResponseEntity<StockTransferResponse> transferStock(@PathVariable UUID companyId, @RequestBody StockTransferRequest request) {

        StockTransferResponse response = stockService.transferStock(companyId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sale/{companyId}")
    public ResponseEntity<StockSaleResponse> recordSale (@PathVariable UUID companyId, @RequestBody StockSaleRequest request) {
        StockSaleResponse response = stockService.recordSale(companyId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/purchase/{companyId}")
    public ResponseEntity<StockPurchaseResponse> purchaseStock (@PathVariable UUID companyId, @RequestBody StockPurchaseRequest request) {
        StockPurchaseResponse response = stockService.recordPurchase(companyId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/audit/{companyId}")
    public ResponseEntity<List<AuditResponse>> auditLogs (@PathVariable UUID companyId) {
        List<AuditResponse> responses = stockService.auditLog(companyId);

        return ResponseEntity.ok(responses);
    }
}
