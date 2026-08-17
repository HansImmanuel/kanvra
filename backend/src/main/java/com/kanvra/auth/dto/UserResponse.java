package com.kanvra.auth.dto;

import com.kanvra.auth.model.User;

/**
 * Standard user representation returned by auth and /me endpoints
 * (SPEC.md §3.2/§3.3). Tokens travel in cookies, never in the body.
 */
public record UserResponse(Long id, String name, String email, String avatarUrl) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getAvatarUrl());
    }
}
