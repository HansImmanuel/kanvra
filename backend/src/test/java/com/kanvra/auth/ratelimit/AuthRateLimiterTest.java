package com.kanvra.auth.ratelimit;

import com.kanvra.common.config.KanvraProperties;
import com.kanvra.common.error.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    @Test
    void rejectsRequestsBeyondLimit() {
        KanvraProperties properties = new KanvraProperties();
        properties.getAuth().setRateLimitPerMinute(3);
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
        properties.getAuth().setRateLimitPerMinute(1);
        AuthRateLimiter limiter = new AuthRateLimiter(properties);

        assertThatCode(() -> limiter.check("ip-a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check("ip-a")).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.check("ip-b")).doesNotThrowAnyException();
    }

    @Test
    void idleBucketsAreEvicted() {
        TestClock clock = new TestClock(Instant.parse("2026-08-18T00:00:00Z"));
        KanvraProperties properties = new KanvraProperties();
        properties.getAuth().setRateLimitPerMinute(1);
        AuthRateLimiter limiter = new AuthRateLimiter(properties, clock);

        limiter.check("expired-ip");
        assertThat(limiter.bucketCount()).isEqualTo(1);

        // More than a window later, the idle bucket is swept away.
        clock.advance(Duration.ofMinutes(2));
        limiter.cleanupExpired();
        assertThat(limiter.bucketCount()).isZero();

        // A fresh window means the same IP gets a fresh bucket.
        assertThatCode(() -> limiter.check("expired-ip")).doesNotThrowAnyException();
    }

    /** Mutable clock so tests can simulate the passage of time. */
    private static final class TestClock extends Clock {

        private Instant instant;

        TestClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}