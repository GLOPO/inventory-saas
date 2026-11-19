package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectOrderRequest(
        @NotBlank(message = "A reason for rejecting must be provided")
        @Size(max = 500, message = "Reason is too long")
        String rejectionReason
) {
}
