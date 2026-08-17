package com.kanvra.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.common.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the CSRF double-submit-cookie pattern (SPEC.md §3.1, TECH_DOC.md
 * §16): for every state-changing request the value of the {@code csrf_token}
 * cookie must equal the {@code X-CSRF-Token} header. The auth endpoints are
 * exempt because they bootstrap the session itself.
 */
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String CSRF_HEADER = "X-CSRF-Token";

    private final ObjectMapper objectMapper;

    public CsrfDoubleSubmitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (SAFE_METHODS.contains(method) || uri.startsWith("/api/v1/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String cookie = CookieService.readCookie(request, CookieService.CSRF_COOKIE);
        String header = request.getHeader(CSRF_HEADER);

        if (cookie == null || header == null || !cookie.equals(header)) {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiError.of(403, "FORBIDDEN", "Invalid or missing CSRF token"));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
