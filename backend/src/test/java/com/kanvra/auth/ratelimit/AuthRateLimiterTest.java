package com.kanvra.auth.ratelimit;

import com.kanvra.common.config.KanvraProperties;
import com.kanvra.common.error.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    @Test
    void rejectsRequestsBeyondLimit() {
        KanvraProperties properties = new KanvraProperties();
        properties.setAuthRateLimitPerMinute(3);
        AuthRateLimiter limiter = new AuthRateLimiter(properties);

        assertThatCode(() -> limiter.check("10.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("10.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("10.0.0.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check("10.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void differentIpsHaveIndependentBuckets() {
        KanvraProperties properties = new KanvraProperties();
        properties.setAuthRateLimitPerMinute(1);
        AuthRateLimiter limiter = new AuthRateLimiter(properties);

        assertThatCode(() -> limiter.check("ip-a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check("ip-a")).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.check("ip-b")).doesNotThrowAnyException();
    }
}