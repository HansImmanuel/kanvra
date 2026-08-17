package com.kanvra.auth.service;

import com.kanvra.auth.dto.LoginRequest;
import com.kanvra.auth.dto.RegisterRequest;
import com.kanvra.auth.model.User;
import com.kanvra.auth.repository.UserRepository;
import com.kanvra.common.error.DuplicateEmailException;
import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.common.error.UnauthorizedException;
import com.kanvra.common.security.JwtService;
import com.kanvra.common.security.JwtTokenType;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh, and current-user lookup (SPEC.md §3).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("An account with this email already exists");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return userRepository.save(user);
    }

    public User login(LoginRequest request) {
        return userRepository.findByEmail(normalizeEmail(request.email()))
                .map(user -> {
                    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                        throw new UnauthorizedException("Invalid email or password");
                    }
                    return user;
                })
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    }

    public User refresh(String rawRefreshToken) {
        Long userId;
        try {
            userId = jwtService.parse(rawRefreshToken, JwtTokenType.REFRESH).id();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Refresh token does not reference a valid user"));
    }

    public User me(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
