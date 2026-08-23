package com.kanvra.common.config;

import com.kanvra.common.config.KanvraProperties.Jwt;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast guard against booting outside dev/test with insecure defaults
 * (code-review finding: nothing stopped a real environment from starting with
 * the default JWT secret, {@code cookies.secure=false}, a wildcard CORS origin,
 * or a too-short JWT secret).
 *
 * <p>Local development runs with no active profile (or {@code dev}/{@code test})
 * and is allowed to use defaults. Any other profile must supply a real JWT
 * secret (≥ 32 bytes), secure cookies, and explicit CORS origins, or the
 * application refuses to start.
 */
@Component
public class InsecureDefaultsGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(InsecureDefaultsGuard.class);

    private static final List<String> DEV_LIKE_PROFILES = List.of("dev", "test");
    private static final int MIN_JWT_SECRET_BYTES = 32;

    private final Environment environment;
    private final KanvraProperties properties;

    public InsecureDefaultsGuard(Environment environment, KanvraProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean devLike = activeProfiles.length == 0
                || Arrays.asList(activeProfiles).stream().anyMatch(DEV_LIKE_PROFILES::contains);
        if (devLike) {
            warnIfJwtSecretTooShort();
            return;
        }

        if (Jwt.DEFAULT_DEV_SECRET.equals(properties.getJwt().getSecret())) {
            throw new IllegalStateException(
                    "Refusing to start outside dev/test with the default JWT secret. Set KANVRA_JWT_SECRET.");
        }
        warnIfJwtSecretTooShort();
        if (!properties.getCookies().isSecure()) {
            throw new IllegalStateException(
                    "Refusing to start outside dev/test with kanvra.cookies.secure=false. "
                    + "Secure cookies are required over HTTPS.");
        }
        List<String> corsOrigins = properties.getCorsOrigins();
        if (corsOrigins == null || corsOrigins.isEmpty() || corsOrigins.contains("*")) {
            throw new IllegalStateException(
                    "Refusing to start outside dev/test with empty or wildcard CORS origins: "
                    + "kanvra.cors.origins must list every allowed frontend origin explicitly.");
        }
    }

    /**
     * HMAC-SHA keys below 32 bytes are weak no matter the profile; warn in dev so
     * it is never silently shipped. Non-dev profiles still fail (see above — short
     * secrets never reach the JwtService with weak entropy in prod).
     */
    private void warnIfJwtSecretTooShort() {
        if (properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            log.warn("KANVRA_JWT_SECRET is shorter than {} bytes; acceptable only for local dev. "
                    + "Production startup is refused.", MIN_JWT_SECRET_BYTES);
        }
    }
}
