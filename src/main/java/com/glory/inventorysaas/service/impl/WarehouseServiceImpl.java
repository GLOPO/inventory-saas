package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.WarehouseRequest;
import com.glory.inventorysaas.dto.response.WarehouseResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.Warehouse;
import com.glory.inventorysaas.mapper.WarehouseMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.WarehouseRepository;
import com.glory.inventorysaas.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {
    private final WarehouseRepository warehouseRepository;
    private final CompanyRepository companyRepository;
    private final WarehouseMapper warehouseMapper;

    @Override
    @Transactional
    public WarehouseResponse createWarehouse(UUID companyId, WarehouseRequest request) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found with ID: " + companyId));

        Warehouse warehouse = Warehouse.builder()
                .code(request.code())
                .name(request.name())
                .company(company)
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);

        return warehouseMapper.toResponse(saved);
    }
}

