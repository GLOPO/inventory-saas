package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.request.ApproveOrderRequest;
import com.glory.inventorysaas.dto.request.PlaceOrderRequest;
import com.glory.inventorysaas.dto.request.RejectOrderRequest;
import com.glory.inventorysaas.dto.response.PlaceOrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    PlaceOrderResponse placeOrder (UUID companyId, PlaceOrderRequest request);

    List<PlaceOrderResponse> incomingOrders ();

    PlaceOrderResponse approveOrder (UUID orderId, ApproveOrderRequest request);

    PlaceOrderResponse rejectOrder (UUID orderId, RejectOrderRequest request);

    PlaceOrderResponse completeOrder (UUID orderId);
}
