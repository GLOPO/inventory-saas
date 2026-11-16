
package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.request.CompanyRegistrationRequest;
import com.glory.inventorysaas.dto.request.CreateManagerRequest;
import com.glory.inventorysaas.dto.request.CreateStaffRequest;
import com.glory.inventorysaas.dto.request.LoginRequest;
import com.glory.inventorysaas.dto.response.AuthResponse;
import com.glory.inventorysaas.dto.response.CompanyResponse;
import com.glory.inventorysaas.dto.response.UserResponse;
import com.glory.inventorysaas.security.JwtService;
import com.glory.inventorysaas.security.UserPrincipal;
import com.glory.inventorysaas.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Operation(description = "User registration", summary = "Register new users")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody CompanyRegistrationRequest request) {

        UserResponse response = userService.registerCompanyAndOwner(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(description = "User login", summary = "Authenticate user and generate JWT")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email(),
                                request.password()
                        )
                );

        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        String token = jwtService.generateToken(userPrincipal);

        UserResponse userResponse = new UserResponse(
                userPrincipal.getUserId(),
                userPrincipal.getUsername(),
                userPrincipal.getRole(),
                new CompanyResponse(
                        userPrincipal.getCompanyId(),
                        userPrincipal.getUser().getCompany().getName()
                )
        );

        AuthResponse response = new AuthResponse(
                token,
                "Bearer",
                userResponse
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/managers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<UserResponse> createManager (@Valid @RequestBody CreateManagerRequest request){
        UserResponse response = userService.createManager(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/staffs")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ResponseEntity<UserResponse> createStaff (@Valid @RequestBody CreateStaffRequest request){
        UserResponse response = userService.createStaff(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
