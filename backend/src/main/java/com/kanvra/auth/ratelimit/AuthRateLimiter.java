package com.kanvra.auth.ratelimit;

import com.kanvra.common.config.KanvraProperties;
import com.kanvra.common.error.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory per-IP rate limiter for the auth endpoints (SPEC.md §16): N
 * requests per minute per IP, then {@code 429 TOO_MANY_REQUESTS}. Bucket4j.
 */
@Component
public class AuthRateLimiter {

    private final int permitsPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimiter(KanvraProperties properties) {
        this.permitsPerMinute = properties.getAuthRateLimitPerMinute();
    }

    public void check(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent(clientIp, this::newBucket);
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many requests, please slow down");
        }
    }

    private Bucket newBucket(String ignored) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(permitsPerMinute, Refill.greedy(permitsPerMinute, Duration.ofMinutes(1))))
                .build();
    }
}
