package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.StockTransferRequest;
import com.glory.inventorysaas.dto.response.StockTransferResponse;
import com.glory.inventorysaas.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
