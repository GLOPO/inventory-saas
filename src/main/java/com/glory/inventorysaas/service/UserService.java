package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.request.ChangePasswordRequest;
import com.glory.inventorysaas.dto.request.CompanyRegistrationRequest;
import com.glory.inventorysaas.dto.request.CreateManagerRequest;
import com.glory.inventorysaas.dto.request.CreateStaffRequest;
import com.glory.inventorysaas.dto.response.AuthResponse;
import com.glory.inventorysaas.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse registerCompanyAndOwner(CompanyRegistrationRequest request);

    UserResponse createManager(CreateManagerRequest request);

    UserResponse createStaff(CreateStaffRequest request);

    List<UserResponse> getAllCompanies();

    List<UserResponse> getAllManagers();

    String changePassword(ChangePasswordRequest request);
}
