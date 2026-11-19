package com.glory.inventorysaas.dto;

import java.io.Serializable;

import lombok.Builder;

@Builder
public record Coordinates(
        double latitude,
        double longitude
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
