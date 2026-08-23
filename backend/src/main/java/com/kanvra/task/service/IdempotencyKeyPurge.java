package com.kanvra.task.service;

import com.kanvra.task.repository.IdempotencyKeyRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled purge of expired {@code idempotency_keys} rows (code-review finding:
 * rows expired after 24h but were only removed lazily on same-key reuse, so the
 * table could grow without bound under churn). Retention is the documented
 * dedup window (24h) plus a small grace period.
 */
@Component
public class IdempotencyKeyPurge {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurge.class);

    /** The dedup window itself is 24h (TaskService); purge anything older. */
    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;

    public IdempotencyKeyPurge(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteByExpiresAtBefore(Instant.now().minus(RETENTION));
        if (removed > 0) {
            log.info("Purged {} expired idempotency-key rows", removed);
        }
    }
}