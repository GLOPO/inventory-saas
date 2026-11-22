
package com.glory.inventorysaas.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private void writeJson(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String error,
            String message
    ) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        response.getWriter().write(
                "{\"timestamp\":\"" + Instant.now()
                        + "\",\"status\":" + status
                        + ",\"error\":\"" + error
                        + "\",\"message\":\"" + message
                        + "\",\"path\":\"" + request.getRequestURI() + "\"}"
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .cors(Customizer.withDefaults())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/docs",
                                "/docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Public endpoints
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/setup-password",
                                "/api/auth/refresh"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders/public/place/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/products/**").permitAll()

                        // User management
                        .requestMatchers(HttpMethod.POST, "/api/auth/managers")
                        .hasRole("OWNER")

                        .requestMatchers(HttpMethod.POST, "/api/auth/staffs")
                        .hasRole("WAREHOUSE_MANAGER")

                        // Inventory read access: all three roles
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/inventory/**"
                        ).hasAnyRole(
                                "OWNER",
                                "WAREHOUSE_MANAGER",
                                "STAFF"
                        )

                        // Inventory write access: manager and staff
                        .requestMatchers("/api/inventory/**")
                        .hasAnyRole("WAREHOUSE_MANAGER", "STAFF")

                        // Stock movement read access: all three roles
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/stock-movement/**"
                        ).hasAnyRole(
                                "OWNER",
                                "WAREHOUSE_MANAGER",
                                "STAFF"
                        )

                        // Stock movement write access: manager and staff
//                        .requestMatchers("/api/stock-movement/**")
//                        .hasAnyRole("WAREHOUSE_MANAGER", "STAFF")

                        // Warehouse creation: OWNER only
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/warehouse/**",
                                "/api/warehouses/**"
                        ).hasRole("OWNER")

                        // Warehouse updates: OWNER only
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/warehouse/**",
                                "/api/warehouses/**"
                        ).hasRole("OWNER")

                        // Warehouse deletion: OWNER only
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/warehouse/**",
                                "/api/warehouses/**"
                        ).hasRole("OWNER")

                        // Product creation: OWNER only
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products/**",
                                "/api/product/**"
                        ).hasRole("OWNER")

                        // Product updates: OWNER only
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/products/**",
                                "/api/product/**"
                        ).hasRole("OWNER")

                        // Product deletion: OWNER only
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/products/**",
                                "/api/product/**"
                        ).hasRole("OWNER")

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                writeJson(
                                        request,
                                        response,
                                        401,
                                        "Unauthorized",
                                        "Authentication is required"
                                )
                        )
                        .accessDeniedHandler((request, response, exception) ->
                                writeJson(
                                        request,
                                        response,
                                        403,
                                        "Forbidden",
                                        "You do not have permission to do this"
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter
    ) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
