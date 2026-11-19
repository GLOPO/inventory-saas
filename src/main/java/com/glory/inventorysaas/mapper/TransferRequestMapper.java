package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.TransferRequestResponse;
import com.glory.inventorysaas.entity.TransferRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransferRequestMapper {

    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "sourceWarehouseName", source = "sourceWarehouse.name")
    @Mapping(target = "destinationWarehouseName", source = "destinationWarehouse.name")
    @Mapping(target = "orderId", source = "order.id")
    TransferRequestResponse toResponse(TransferRequest request);
}
