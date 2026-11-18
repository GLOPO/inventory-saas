package com.glory.inventorysaas.dto;

import lombok.Builder;

@Builder
public record Coordinates(
        double latitude,
        double longitude
) {
}
