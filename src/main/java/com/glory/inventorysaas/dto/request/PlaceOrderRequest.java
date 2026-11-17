package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlaceOrderRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String customerEmail,

        @NotNull(message = "Quantity is required")
        @Min(1)
        Integer quantity,

        @NotBlank(message = "Delivery address is required")
        String deliveryLocation,

        @NotBlank(message = "Product is required")
        String requestedProductName,

        UUID selectedProductId
) {
}
