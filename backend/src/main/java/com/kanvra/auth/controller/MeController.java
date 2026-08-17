package com.kanvra.auth.controller;

import com.kanvra.auth.dto.UserResponse;
import com.kanvra.auth.service.AuthService;
import com.kanvra.common.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the currently authenticated user (session restore helper for the
 * frontend). Documented alongside the auth endpoints; not part of SPEC §3.
 */
@RestController
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/api/v1/me")
    public UserResponse me(Authentication authentication) {
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        return UserResponse.from(authService.me(principal.id()));
    }
}
