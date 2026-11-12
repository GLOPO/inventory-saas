package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.request.CompanyRegistrationRequest;
import com.glory.inventorysaas.dto.response.UserResponse;

public interface UserService {

    UserResponse registerCompanyAndOwner(CompanyRegistrationRequest request);
}
