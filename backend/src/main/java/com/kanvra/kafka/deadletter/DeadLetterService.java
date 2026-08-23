package com.kanvra.kafka.deadletter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists permanently-failing Kafka messages (docs/TECH_DOC.md §20) so a
 * poison pill is acked instead of blocking its partition forever, and provides
 * the retention purge. Recovery of parked messages is manual/offline for the
 * MVP (documented in README); nothing here attempts automatic reprocessing.
 */
@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private static final Duration RETENTION = Duration.ofDays(30);

    private final DeadLetterEventRepository repository;

    public DeadLetterService(DeadLetterEventRepository repository) {
        this.repository = repository;
    }

    /** Parks a message without envelope metadata (e.g. unparseable JSON). */
    @Transactional
    public void record(String consumerGroup, String rawMessage, Throwable cause) {
        record(consumerGroup, null, null, rawMessage, reasonOf(cause));
    }

    /** Parks a message whose envelope parsed well enough to extract eventId/eventType. */
    @Transactional
    public void record(String consumerGroup, String eventId, String eventType, String rawMessage, Throwable cause) {
        record(consumerGroup, eventId, eventType, rawMessage, reasonOf(cause));
    }

    @Transactional
    public void record(String consumerGroup, String eventId, String eventType, String rawMessage, String reason) {
        DeadLetterEvent row = new DeadLetterEvent();
        row.setConsumerGroup(consumerGroup);
        row.setEventId(eventId);
        row.setEventType(eventType);
        row.setRawMessage(rawMessage);
        row.setReason(reason);
        repository.save(row);
    }

    /** Most recent parked messages for manual inspection. */
    @Transactional(readOnly = true)
    public List<DeadLetterEvent> latest() {
        return repository.findTop100ByOrderByIdDesc();
    }

    @Scheduled(fixedDelay = 86_400_000, initialDelay = 86_400_000)
    @Transactional
    public void purgeOld() {
        int removed = repository.deleteByCreatedAtBefore(Instant.now().minus(RETENTION));
        if (removed > 0) {
            log.info("Purged {} dead-letter rows older than {} days", removed, RETENTION.toDays());
        }
    }

    /** Compacts a throwable into the bounded {@code reason} column. */
    public static String reasonOf(Throwable t) {
        if (t == null) {
            return "unknown failure";
        }
        String message = t.getMessage();
        String text = (message == null || message.isBlank())
                ? t.getClass().getSimpleName()
                : t.getClass().getSimpleName() + ": " + message;
        return text.length() <= 512 ? text : text.substring(0, 512);
    }
}