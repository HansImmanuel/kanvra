package com.kanvra.auth.ratelimit;

import com.kanvra.common.config.KanvraProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Resolves the effective client IP for rate limiting.
 *
 * <p>The {@code X-Forwarded-For} header is only honored when the request
 * arrives from a configured <em>trusted</em> reverse proxy; otherwise it is
 * client-supplied and trivially spoofable (a fresh header value per request
 * defeats the brute-force protection by minting a new rate-limit bucket).
 * Until a real reverse proxy sits in front of the application, every client
 * is identified by {@code request.getRemoteAddr()}.
 *
 * <p>See docs/TECH_DOC.md §16 and SPEC.md §16.
 */
@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(KanvraProperties properties) {
        this.trustedProxies = new HashSet<>(properties.getAuth().getTrustedProxies());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.contains(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }
}
