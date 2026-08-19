package com.kanvra.common.config;

import com.kanvra.common.config.KanvraProperties.Jwt;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast guard against booting outside dev/test with insecure defaults
 * (code-review finding: nothing stopped a real environment from starting with
 * the default JWT secret or {@code cookies.secure=false}).
 *
 * <p>Local development runs with no active profile (or {@code dev}/{@code test})
 * and is allowed to use defaults. Any other profile must supply a real JWT
 * secret and secure cookies, or the application refuses to start.
 */
@Component
public class InsecureDefaultsGuard implements InitializingBean {

    private static final List<String> DEV_LIKE_PROFILES = List.of("dev", "test");

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
            return;
        }

        if (Jwt.DEFAULT_DEV_SECRET.equals(properties.getJwt().getSecret())) {
            throw new IllegalStateException(
                    "Refusing to start outside dev/test with the default JWT secret. Set KANVRA_JWT_SECRET.");
        }
        if (!properties.getCookies().isSecure()) {
            throw new IllegalStateException(
                    "Refusing to start outside dev/test with kanvra.cookies.secure=false. " +
                    "Secure cookies are required over HTTPS.");
        }
    }
}
