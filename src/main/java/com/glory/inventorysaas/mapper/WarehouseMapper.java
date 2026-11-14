package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.WarehouseResponse;
import com.glory.inventorysaas.entity.Warehouse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WarehouseMapper {

    @Mapping(target = "companyName", source = "company.name")
    WarehouseResponse toResponse(Warehouse warehouse);
}
