package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.CompanyRegistrationRequest;
import com.glory.inventorysaas.dto.response.UserResponse;
import com.glory.inventorysaas.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @Operation(description = "User registration", summary = "register new users")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody CompanyRegistrationRequest request) {
        UserResponse response = userService.registerCompanyAndOwner(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
