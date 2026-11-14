package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.AuditResponse;
import com.glory.inventorysaas.dto.response.StockSaleResponse;
import com.glory.inventorysaas.dto.response.StockPurchaseResponse;
import com.glory.inventorysaas.dto.response.StockTransferResponse;
import com.glory.inventorysaas.entity.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockMovementMapper {

    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "sourceWarehouseName", source = "sourceWarehouse.name")
    @Mapping(target = "destinationWarehouseName", source = "destinationWarehouse.name")
    StockTransferResponse toTransferResponse(StockMovement movement);

    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "sourceWarehouseName", source = "sourceWarehouse.name")
    StockSaleResponse toSaleResponse(StockMovement movement);

    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "destinationWarehouseName", source = "destinationWarehouse.name")
    StockPurchaseResponse toPurchaseResponse(StockMovement movement);

    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "sourceWarehouseName", source = "sourceWarehouse.name")
    @Mapping(target = "destinationWarehouseName", source = "destinationWarehouse.name")
    AuditResponse toAuditResponse(StockMovement movement);
}
