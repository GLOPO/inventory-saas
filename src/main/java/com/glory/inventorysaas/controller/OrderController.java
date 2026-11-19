package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.*;
import com.glory.inventorysaas.dto.response.PlaceOrderResponse;
import com.glory.inventorysaas.dto.response.TransferRequestResponse;
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

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<PlaceOrderResponse> rejectOrder (@PathVariable UUID orderId,
                                                           @Valid @RequestBody RejectOrderRequest request) {
        PlaceOrderResponse response = orderService.rejectOrder(orderId, request);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @GetMapping("/warehouse")
    public ResponseEntity<List<PlaceOrderResponse>> orders () {
        List<PlaceOrderResponse> responses = orderService.warehouseOrders();

        return ResponseEntity.ok(responses);
    }

    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @PostMapping("/{orderId}/complete")
    public ResponseEntity<PlaceOrderResponse> completeOrder(@PathVariable UUID orderId) {
        PlaceOrderResponse response = orderService.completeOrder(orderId);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @PostMapping("/{orderId}/reroute")
    public ResponseEntity<PlaceOrderResponse> rerouteOrder(@PathVariable UUID orderId, @Valid @RequestBody RerouteOrderRequest request) {
        PlaceOrderResponse response = orderService.rerouteOrder(orderId, request);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @PostMapping("/transfer-requests")
    public ResponseEntity<TransferRequestResponse> transferRequest(@Valid @RequestBody TransferStockRequest request) {
        TransferRequestResponse response = orderService.createTransfer(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @GetMapping("/incoming-requests")
    public ResponseEntity<List<TransferRequestResponse>> incomingRequest() {
        List<TransferRequestResponse> responses = orderService.incomingTransferRequests();

        return ResponseEntity.ok(responses);
    }

    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @PostMapping("/transfer/{requestId}/accept")
    public ResponseEntity<TransferRequestResponse> acceptTransfer(@PathVariable UUID requestId) {
        TransferRequestResponse response = orderService.acceptTransfer(requestId);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @PostMapping("/transfer/{requestId}/decline")
    public ResponseEntity<TransferRequestResponse> declineTransfer(@PathVariable UUID requestId) {
        TransferRequestResponse response = orderService.declineTransfer(requestId);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @GetMapping("/transfer-requests")
    public ResponseEntity<List<TransferRequestResponse>> myRequests() {
        List<TransferRequestResponse> response = orderService.myRequests();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
