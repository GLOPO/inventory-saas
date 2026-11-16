package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.CompanyRegistrationRequest;
import com.glory.inventorysaas.dto.request.CreateManagerRequest;
import com.glory.inventorysaas.dto.request.CreateStaffRequest;
import com.glory.inventorysaas.dto.response.AuthResponse;
import com.glory.inventorysaas.dto.response.CompanyResponse;
import com.glory.inventorysaas.dto.response.UserResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.Warehouse;
import com.glory.inventorysaas.entity.enums.Role;
import com.glory.inventorysaas.entity.User;
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.mapper.UserMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.UserRepository;
import com.glory.inventorysaas.repository.WarehouseRepository;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final SecurityUtils securityUtils;
    private final WarehouseRepository warehouseRepository;

    @Override
    @Transactional
    public UserResponse registerCompanyAndOwner(CompanyRegistrationRequest request) {
        Company company = Company.builder()
                .name(request.name())
                .build();

        Company savedCompany = companyRepository.save(company);

        User owner = User.builder()
                .company(savedCompany)
                .email(request.adminEmail())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.OWNER)
                .build();

        User savedOwner = userRepository.save(owner);

        return userMapper.toResponse(savedOwner);
    }

    @Override
    public UserResponse createManager(CreateManagerRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        if(userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));

        if (!warehouse.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Warehouse not found"
            );
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        User user = User.builder()
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .company(company)
                .warehouse(warehouse)
                .role(Role.WAREHOUSE_MANAGER)
                .build();

        User saved = userRepository.save(user);

        return new UserResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                new CompanyResponse(companyId, company.getName())
        );
    }

    @Override
    public UserResponse createStaff(CreateStaffRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        if(warehouseId == null) {
            throw new AccessDeniedException("Access Denied. You must be assigned to the warehouse to create a staff");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company Not Found"));

        Warehouse warehouse = warehouseRepository.findByIdAndCompanyId(warehouseId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not Found"));

        User staff = User.builder()
                .company(company)
                .email(request.email())
                .role(Role.STAFF)
                .password(passwordEncoder.encode(request.password()))
                .warehouse(warehouse)
                .build();

        User saved = userRepository.save(staff);

        return new UserResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),

                new CompanyResponse(companyId, company.getName())
        );
    }

    @Override
    public List<UserResponse> getAllCompanies() {
        List<User> users = userRepository.findAll();

        return users.stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    public List<UserResponse> getAllManagers() {
        List<User> users = userRepository.findByRole(Role.WAREHOUSE_MANAGER);

        return users.stream()
                .map(userMapper::toResponse)
                .toList();
    }
}
