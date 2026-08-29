package com.kanvra.outbox.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kanvra.outbox.model.OutboxEvent;
import com.kanvra.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Outbox publisher retry behavior (Sprint 4 hardening): consecutive broker
 * failures arm an exponential backoff so the next {@code @Scheduled} tick skips
 * instead of hammering a down broker, and a cleared backoff resumes publishing
 * (docs/TECH_DOC.md §8).
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper mapper = new ObjectMapper();
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, kafkaTemplate, mapper);
    }

    private OutboxEvent row() {
        OutboxEvent row = new OutboxEvent();
        ReflectionTestUtils.setField(row, "id", 1L);
        row.setEventId(UUID.randomUUID());
        row.setEventType("task.created");
        row.setAggregateType("task");
        row.setAggregateId(42L);
        row.setProjectId(7L);
        row.setActorId(1L);
        row.setPayload(JsonNodeFactory.instance.objectNode());
        return row;
    }

    @Test
    void failedPublishArmsBackoffAndSkipsImmediateNextTick() {
        when(repository.findTop100ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(row()));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("broker down"));

        publisher.publishPending();
        assertThat(publisher.consecutiveFailures()).isEqualTo(1);

        // The immediately-following tick is inside the backoff window: it must not
        // poll the DB or call the broker again.
        publisher.publishPending();

        verify(repository, times(1)).findTop100ByPublishedAtIsNullOrderByIdAsc();
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
        verifyNoMoreInteractions(repository, kafkaTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void clearedBackoffResumesPublishingAndMarksRows() {
        when(repository.findTop100ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(row()));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("broker down"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishPending(); // fail once -> backoff armed
        assertThat(publisher.consecutiveFailures()).isEqualTo(1);

        // Simulate elapsed backoff, then the next tick must publish + mark.
        ReflectionTestUtils.setField(publisher, "nextAttemptAt", Instant.EPOCH);
        publisher.publishPending();

        assertThat(publisher.consecutiveFailures()).isZero();
        verify(repository, times(2)).findTop100ByPublishedAtIsNullOrderByIdAsc();
        verify(repository).markPublished(anyList(), any(Instant.class));
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void backoffGrowsExponentiallyWithJitterAndIsBounded() {
        // Each exponent's full jitter range sits strictly below the next
        // exponent's floor, so samples never regress.
        for (int i = 1; i <= 5; i++) {
            long maxCurrent = Long.MIN_VALUE;
            long minNext = Long.MAX_VALUE;
            for (int s = 0; s < 50; s++) {
                maxCurrent = Math.max(maxCurrent, OutboxPublisher.computeBackoffMillis(i));
                minNext = Math.min(minNext, OutboxPublisher.computeBackoffMillis(i + 1));
            }
            assertThat(maxCurrent).isLessThan(minNext);
        }

        // The ceiling applies regardless of how large the exponent grows.
        for (int s = 0; s < 100; s++) {
            assertThat(OutboxPublisher.computeBackoffMillis(12)).isLessThanOrEqualTo(OutboxPublisher.MAX_DELAY_MS);
        }
    }
}