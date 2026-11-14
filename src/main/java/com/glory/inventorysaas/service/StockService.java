package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.response.*;
import com.glory.inventorysaas.dto.request.StockPurchaseRequest;
import com.glory.inventorysaas.dto.request.StockSaleRequest;
import com.glory.inventorysaas.dto.request.StockTransferRequest;

import java.util.List;
import java.util.UUID;

public interface StockService {

    StockTransferResponse transferStock ( StockTransferRequest request);


    StockSaleResponse recordSale ( StockSaleRequest request);

    StockPurchaseResponse recordPurchase (StockPurchaseRequest request);

    List<AuditResponse> auditLog ();
}
