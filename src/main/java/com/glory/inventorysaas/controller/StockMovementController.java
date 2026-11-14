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

@RestController
@RequestMapping("/api/stock-movement")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockService stockService;

    @PostMapping("/transfer")
    public ResponseEntity<StockTransferResponse> transferStock( @RequestBody StockTransferRequest request) {

        StockTransferResponse response = stockService.transferStock( request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sale")
    public ResponseEntity<StockSaleResponse> recordSale ( @RequestBody StockSaleRequest request) {
        StockSaleResponse response = stockService.recordSale( request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/purchase")
    public ResponseEntity<StockPurchaseResponse> purchaseStock ( @RequestBody StockPurchaseRequest request) {
        StockPurchaseResponse response = stockService.recordPurchase(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/audit")
    public ResponseEntity<List<AuditResponse>> auditLogs () {
        List<AuditResponse> responses = stockService.auditLog();

        return ResponseEntity.ok(responses);
    }
}
