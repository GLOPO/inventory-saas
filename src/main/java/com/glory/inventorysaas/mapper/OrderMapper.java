package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.request.PlaceOrderRequest;
import com.glory.inventorysaas.dto.response.PlaceOrderResponse;
import com.glory.inventorysaas.entity.CustomerOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "matchedProductName", source = "product.name")
    PlaceOrderResponse toCustomerOrderResponse(CustomerOrder order);
}
