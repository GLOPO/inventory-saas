package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.InventoryResponse;
import com.glory.inventorysaas.entity.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    InventoryResponse toResponse(Inventory inventory);
}
