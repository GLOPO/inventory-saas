package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.response.*;
import com.glory.inventorysaas.dto.request.StockPurchaseRequest;
import com.glory.inventorysaas.dto.request.StockSaleRequest;
import com.glory.inventorysaas.dto.request.StockTransferRequest;

import java.util.List;
import java.util.UUID;

public interface StockService {

    StockTransferResponse transferStock (UUID companyId, StockTransferRequest request);


    StockSaleResponse recordSale (UUID companyId, StockSaleRequest request);

    StockPurchaseResponse recordPurchase (UUID companyId, StockPurchaseRequest request);

    List<AuditResponse> auditLog (UUID companyId);
}
