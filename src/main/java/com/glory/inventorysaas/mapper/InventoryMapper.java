package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.InventoryResponse;
import com.glory.inventorysaas.entity.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    InventoryResponse toResponse(Inventory inventory);
}
