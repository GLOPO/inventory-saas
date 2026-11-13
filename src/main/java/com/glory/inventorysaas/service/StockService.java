package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.request.StockTransferRequest;
import com.glory.inventorysaas.dto.response.StockTransferResponse;

import java.util.UUID;

public interface StockService {

    StockTransferResponse transferStock (UUID companyId, StockTransferRequest request);
}
