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
 * cookie must equal the {@code X-CSRF-Token} header.
 *
 * <p>Exemptions are deliberately narrow (code-review hardening):
 * <ul>
 *   <li>only the session-bootstrapping auth endpoints
 *       ({@code /login}, {@code /register}, {@code /refresh}) are exempt —
 *       {@code /logout} is NOT, closing logout-CSRF;</li>
 *   <li>requests carrying no session cookie at all are exempt: a request with
 *       neither an {@code access_token} nor a {@code csrf_token} cookie is a
 *       non-browser client (typically Bearer-authenticated) and is not subject
 *       to CSRF in the first place.</li>
 * </ul>
 */
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final Set<String> AUTH_BOOTSTRAP_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh");

    private final ObjectMapper objectMapper;

    public CsrfDoubleSubmitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (SAFE_METHODS.contains(method) || AUTH_BOOTSTRAP_PATHS.contains(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Non-browser clients authenticate via Authorization: Bearer and carry no
        // session cookies; they are not subject to CSRF. Without this, the first
        // Bearer-authenticated mutation would always 403.
        boolean hasSessionCookie = CookieService.readCookie(request, CookieService.ACCESS_COOKIE) != null
                || CookieService.readCookie(request, CookieService.CSRF_COOKIE) != null;
        if (!hasSessionCookie) {
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

