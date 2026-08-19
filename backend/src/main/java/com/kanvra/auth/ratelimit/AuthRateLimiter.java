package com.kanvra.auth.ratelimit;

import com.kanvra.common.config.KanvraProperties;
import com.kanvra.common.error.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory per-IP rate limiter for the auth endpoints (SPEC.md §16): N
 * requests per minute per IP, then {@code 429 TOO_MANY_REQUESTS}. Bucket4j.
 *
 * <p>Buckets are evicted once idle past the window so a spoofed or transient
 * IP space can never grow the backing map without bound (code-review finding:
 * the previous implementation never evicted entries). Cleanup runs lazily on
 * every Nth check and on a fixed schedule via {@link Scheduled}.
 */
@Component
public class AuthRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long SWEEP_EVERY_N_CHECKS = 1_000;

    private final int permitsPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private final AtomicLong checksSinceSweep = new AtomicLong();

    @Autowired
    public AuthRateLimiter(KanvraProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Package-private constructor for deterministic time-based tests. */
    AuthRateLimiter(KanvraProperties properties, Clock clock) {
        this.permitsPerMinute = properties.getAuth().getRateLimitPerMinute();
        this.clock = clock;
    }

    public void check(String clientIp) {
        if (checksSinceSweep.incrementAndGet() % SWEEP_EVERY_N_CHECKS == 0) {
            cleanupExpired();
        }

        Instant now = clock.instant();
        BucketEntry entry = buckets.compute(clientIp, (key, current) -> {
            if (current == null || current.expired(now)) {
                return new BucketEntry(newBucket(), now);
            }
            return new BucketEntry(current.bucket(), now);
        });

        if (!entry.bucket().tryConsume(1)) {
            throw new RateLimitExceededException("Too many requests, please slow down");
        }
    }

    /** Removes buckets that have not been touched within the rate-limit window. */
    public void cleanupExpired() {
        Instant now = clock.instant();
        buckets.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    /** Test/observability accessor for the live bucket count. */
    int bucketCount() {
        return buckets.size();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scheduledCleanup() {
        cleanupExpired();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(permitsPerMinute, Refill.greedy(permitsPerMinute, WINDOW)))
                .build();
    }

    private record BucketEntry(Bucket bucket, Instant lastAccess) {

        boolean expired(Instant now) {
            return now.isAfter(lastAccess.plus(WINDOW));
        }
    }
}

