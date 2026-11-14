package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.WarehouseRequest;
import com.glory.inventorysaas.dto.response.WarehouseResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.Warehouse;
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.mapper.WarehouseMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.WarehouseRepository;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {
    private final WarehouseRepository warehouseRepository;
    private final CompanyRepository companyRepository;
    private final WarehouseMapper warehouseMapper;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    @CacheEvict(value = "warehouse", key = "@securityUtils.getCurrentCompanyCacheKey()")
    public WarehouseResponse createWarehouse( WarehouseRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with ID: " + companyId));

        Warehouse warehouse = Warehouse.builder()
                .code(request.code())
                .name(request.name())
                .company(company)
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);

        return warehouseMapper.toResponse(saved);
    }

    @Override
    @Cacheable(value = "warehouse", key = "@securityUtils.getCurrentCompanyCacheKey()")
    public List<WarehouseResponse> getAllWarehouse() {
        UUID companyId = securityUtils.getCurrentCompanyId();

        List<Warehouse> warehouses = warehouseRepository.findByCompanyId(companyId);

        return warehouses.stream()
                .map(warehouseMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "warehouse", key = "@securityUtils.getCurrentCompanyCacheKey()")
    public WarehouseResponse update(UUID warehouseId, WarehouseRequest request) {

        UUID companyId = securityUtils.getCurrentCompanyId();

        Warehouse warehouse = warehouseRepository.findByIdAndCompanyId(warehouseId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse not found"
                ));

        if (!Objects.equals(warehouse.getCode(), request.code())) {

            boolean codeExists = warehouseRepository
                    .existsByCompanyIdAndCodeAndIdNot(
                            companyId,
                            request.code(),
                            warehouseId
                    );

            if (codeExists) {
                throw new IllegalArgumentException(
                        "Another warehouse in your company exists with this code: "
                                + request.code()
                );
            }
        }

        warehouse.setCode(request.code());
        warehouse.setName(request.name());

        Warehouse updated = warehouseRepository.save(warehouse);

        return warehouseMapper.toResponse(updated);
    }
}

