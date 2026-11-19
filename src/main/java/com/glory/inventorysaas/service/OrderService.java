package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.request.*;
import com.glory.inventorysaas.dto.response.PlaceOrderResponse;
import com.glory.inventorysaas.dto.response.TransferRequestResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    PlaceOrderResponse placeOrder (UUID companyId, PlaceOrderRequest request);

    List<PlaceOrderResponse> incomingOrders ();

    PlaceOrderResponse approveOrder (UUID orderId, ApproveOrderRequest request);

    PlaceOrderResponse rejectOrder (UUID orderId, RejectOrderRequest request);

    PlaceOrderResponse completeOrder (UUID orderId);

    List<PlaceOrderResponse> warehouseOrders();

    PlaceOrderResponse rerouteOrder(UUID orderId, RerouteOrderRequest request);

    TransferRequestResponse createTransfer (TransferStockRequest request);

    List<TransferRequestResponse> incomingTransferRequests();

    TransferRequestResponse acceptTransfer(UUID requestId);

    TransferRequestResponse declineTransfer(UUID requestId);

    List<TransferRequestResponse> myRequests();
}
