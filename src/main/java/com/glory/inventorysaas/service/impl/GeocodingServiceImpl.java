package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.Coordinates;
import com.glory.inventorysaas.service.GeocodingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Service
public class GeocodingServiceImpl implements GeocodingService {
    private final RestClient restClient;
    private static final Logger log = LoggerFactory.getLogger(GeocodingServiceImpl.class);

    public GeocodingServiceImpl() {
        this.restClient = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "inventory-saas-learning-app")
                .build();
    }

    private record NominatimResult(
            String lat,
            String lon
    ) {}

    @Override
    @Cacheable(value = "geocoding", key = "#address")
    public Optional<Coordinates> getLocation(String address) {
        List<NominatimResult> results;

        try {
            results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimResult>>() {});
        } catch (RestClientException ex) {
            log.error("Failed to retrieve location for address: {}", address, ex);
            return Optional.empty();
        }

        if(results == null || results.isEmpty()) {
            return Optional.empty();
        }

        NominatimResult result = results.get(0);

        Coordinates coordinates = Coordinates.builder()
                .latitude(Double.parseDouble(result.lat()))
                .longitude(Double.parseDouble(result.lon()))
                .build();

        return Optional.of(coordinates);
    }
}
