package com.kanvra.common.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Central Kanvra application configuration bound from the {@code kanvra.*}
 * property namespace (which in turn is driven by environment variables).
 *
 * <p>Source of truth: docs/TECH_DOC.md §16, §18 and docs/SPEC.md §3.</p>
 */
@Component
@ConfigurationProperties(prefix = "kanvra")
public class KanvraProperties {

    private Jwt jwt = new Jwt();
    private Cookies cookies = new Cookies();
    private List<String> corsOrigins = new ArrayList<>(List.of("http://localhost:3000"));
    private int authRateLimitPerMinute = 5;

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Cookies getCookies() {
        return cookies;
    }

    public void setCookies(Cookies cookies) {
        this.cookies = cookies;
    }

    public List<String> getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(List<String> corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public int getAuthRateLimitPerMinute() {
        return authRateLimitPerMinute;
    }

    public void setAuthRateLimitPerMinute(int authRateLimitPerMinute) {
        this.authRateLimitPerMinute = authRateLimitPerMinute;
    }

    /** JWT signing configuration. */
    public static class Jwt {
        private String secret = "dev-only-change-me-in-prod-base64-secret";
        private Duration accessTokenTtl = Duration.ofMinutes(30);
        private Duration refreshTokenTtl = Duration.ofDays(7);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }
    }

    /** Cookie flags. */
    public static class Cookies {
        private boolean secure = false;

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }
    }
}
