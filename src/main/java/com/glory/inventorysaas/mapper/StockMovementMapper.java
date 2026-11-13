package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.request.StockTransferRequest;
import com.glory.inventorysaas.dto.response.StockTransferResponse;
import com.glory.inventorysaas.entity.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockMovementMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "sourceWarehouseName", source = "sourceWarehouse.name")
    @Mapping(target = "destinationWarehouseName", source = "destinationWarehouse.name")
    StockTransferResponse toResponse(StockMovement movement);
}
