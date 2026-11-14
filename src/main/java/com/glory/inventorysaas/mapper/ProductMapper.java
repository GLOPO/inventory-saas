package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.ProductResponse;
import com.glory.inventorysaas.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "companyName", source = "company.name")
    ProductResponse toResponse(Product product);
}
