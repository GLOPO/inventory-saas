package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.ApproveOrderRequest;
import com.glory.inventorysaas.dto.request.PlaceOrderRequest;
import com.glory.inventorysaas.dto.response.PlaceOrderResponse;
import com.glory.inventorysaas.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/public/place/{companyId}")
    public ResponseEntity<PlaceOrderResponse> placeOrder(@PathVariable UUID companyId,
                                                         @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResponse response = orderService.placeOrder(companyId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/all")
    public ResponseEntity<List<PlaceOrderResponse>> allOrders() {
        List<PlaceOrderResponse> responses = orderService.incomingOrders();

        return ResponseEntity.ok(responses);
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/{orderId}/approve")
    public ResponseEntity<PlaceOrderResponse> approveOrder (@PathVariable UUID orderId,
                                                            @Valid @RequestBody ApproveOrderRequest request) {
        PlaceOrderResponse response = orderService.approveOrder(orderId, request);

        return ResponseEntity.ok(response);
    }
}
