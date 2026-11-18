package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.Coordinates;

import java.util.Optional;

public interface GeocodingService {

    Optional<Coordinates> getLocation(String address);
}
