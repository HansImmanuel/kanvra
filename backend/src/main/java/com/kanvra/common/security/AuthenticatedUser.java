package com.kanvra.common.security;

import com.kanvra.auth.model.User;

/**
 * The authenticated principal carried in the Spring Security context.
 * Populated by {@link JwtAuthenticationFilter} and used by controllers to
 * identify the current user without a DB hit on every request.
 */
public record AuthenticatedUser(Long id, String name, String email) {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getName(), user.getEmail());
    }
}
