package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.ChangePasswordRequest;
import com.glory.inventorysaas.dto.request.CompanyRegistrationRequest;
import com.glory.inventorysaas.dto.request.CreateManagerRequest;
import com.glory.inventorysaas.dto.request.CreateStaffRequest;
import com.glory.inventorysaas.dto.response.CompanyResponse;
import com.glory.inventorysaas.dto.response.UserResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.User;
import com.glory.inventorysaas.entity.Warehouse;
import com.glory.inventorysaas.entity.enums.Role;
import com.glory.inventorysaas.event.UserProvisionedEvent;
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.mapper.UserMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.UserRepository;
import com.glory.inventorysaas.repository.WarehouseRepository;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.RefreshTokenService;
import com.glory.inventorysaas.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
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
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenService refreshTokenService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateRandomPassword() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"all-users", "all-owners"}, allEntries = true)
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
                .mustChangePassword(false)
                .build();

        User savedOwner = userRepository.save(owner);

        return userMapper.toResponse(savedOwner);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"all-users", "all-managers"}, key = "@securityUtils.getCurrentCompanyId()")
    public UserResponse createManager(CreateManagerRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        if (userRepository.existsByEmail(request.email())) {
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
                .email(request.email())
                .company(company)
                .warehouse(warehouse)
                .role(Role.WAREHOUSE_MANAGER)
                .mustChangePassword(true)
                .password(passwordEncoder.encode(generateRandomPassword()))
                .build();

        User saved = userRepository.save(user);

        eventPublisher.publishEvent(
                new UserProvisionedEvent(
                        saved.getId(),
                        companyId,
                        saved.getEmail(),
                        saved.getRole().name(),
                        company.getName(),
                        warehouse.getName()
                )
        );

        return new UserResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                new CompanyResponse(companyId, company.getName()),
                saved.isMustChangePassword()
        );
    }

    @Override
    @Transactional
    @CacheEvict(value = {"all-users", "all-staffs"}, key = "@securityUtils.getCurrentCompanyId()")
    public UserResponse createStaff(CreateStaffRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        if (warehouseId == null) {
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
                .warehouse(warehouse)
                .mustChangePassword(true)
                .password(passwordEncoder.encode(generateRandomPassword()))
                .build();

        User saved = userRepository.save(staff);

        eventPublisher.publishEvent(
                new UserProvisionedEvent(
                        saved.getId(),
                        companyId,
                        saved.getEmail(),
                        saved.getRole().name(),
                        company.getName(),
                        warehouse.getName()
                )
        );

        return new UserResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                new CompanyResponse(companyId, company.getName()),
                saved.isMustChangePassword()
        );
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "all-users", key = "@securityUtils.getCurrentCompanyId()")
    public List<UserResponse> getAllCompanies() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        List<User> users = userRepository.findByCompanyId(companyId);

        return users.stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "all-managers", key = "@securityUtils.getCurrentCompanyId()")
    public List<UserResponse> getAllManagers() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        List<User> users = userRepository.findByCompanyIdAndRole(companyId, Role.WAREHOUSE_MANAGER);

        return users.stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public String changePassword(ChangePasswordRequest request) {
        UUID userId = securityUtils.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("You cannot reuse your previous password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        refreshTokenService.revokeForUser(user.getId());
        return "Password changed successfully";
    }
}