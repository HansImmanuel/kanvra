package com.kanvra.common.security;

/**
 * Distinguishes access tokens from refresh tokens so a token of one kind can
 * never be used as the other (SPEC.md §3.1).
 */
public enum JwtTokenType {
    ACCESS,
    REFRESH
}
