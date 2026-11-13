package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.CompanyRegistrationRequest;
import com.glory.inventorysaas.dto.response.UserResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.Role;
import com.glory.inventorysaas.entity.User;
import com.glory.inventorysaas.mapper.UserMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.UserRepository;
import com.glory.inventorysaas.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

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
    public List<UserResponse> getAllCompanies() {
        List<User> users = userRepository.findAll();

        return users.stream()
                .map(userMapper::toResponse)
                .toList();
    }
}
