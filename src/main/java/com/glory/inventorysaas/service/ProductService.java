package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.request.ProductRequest;
import com.glory.inventorysaas.dto.response.ProductResponse;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ProductResponse create( ProductRequest request);

    List<ProductResponse> getAllProducts();

    ProductResponse update( UUID productId, ProductRequest request);
}
