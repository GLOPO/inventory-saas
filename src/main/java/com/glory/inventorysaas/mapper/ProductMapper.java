package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.ProductResponse;
import com.glory.inventorysaas.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "companyId", source = "company.id")
    ProductResponse toResponse(Product product);
}
