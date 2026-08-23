package com.kanvra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.activity.model.Activity;
import com.kanvra.activity.repository.ActivityRepository;
import com.kanvra.kafka.deadletter.DeadLetterService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ActivityConsumer failure semantics (docs/SPEC.md §14, AGENT.md §12,
 * TECH_DOC.md §20 — Sprint 4 DLT): malformed or structurally broken messages
 * are parked in the dead-letter table and acked (a poison pill cannot block the
 * partition forever); transient processing failures are rethrown so Kafka
 * redelivers; only genuine duplicate delivery (unique events.event_id already
 * present) is a silent no-op.
 */
@ExtendWith(MockitoExtension.class)
class ActivityConsumerTest {

    @Mock private ActivityRepository repository;
    @Mock private DeadLetterService deadLetterService;

    private final ObjectMapper mapper = new ObjectMapper();

    private ActivityConsumer consumer() {
        return new ActivityConsumer(repository, mapper, deadLetterService);
    }

    private static final String VALID_ENVELOPE = """
            {"eventId":"11111111-1111-1111-1111-111111111111",
             "eventType":"task.created",
             "occurredAt":"2026-08-18T09:00:00Z",
             "actorId":1,
             "projectId":1,
             "aggregateType":"task",
             "aggregateId":5,
             "payload":{"taskId":5,"taskTitle":"Dup","columnId":1,"columnName":"TODO","actorName":"Hans"}}
            """;

    @Test
    void malformedEnvelopeIsParkedInDeadLetterTableNotAckedSilently() {
        String raw = "this is not JSON {";
        assertThatCode(() -> consumer().onDomainEvent(raw)).doesNotThrowAnyException();
        verify(deadLetterService).record(eq("kanvra-activity"), eq(raw), any(Throwable.class));
        verifyNoInteractions(repository);
    }

    @Test
    void structurallyBrokenEnvelopeIsParkedInDeadLetterTable() {
        String raw = "{\"eventType\":\"task.created\",\"payload\":{\"taskTitle\":\"No event id\"}}";
        assertThatCode(() -> consumer().onDomainEvent(raw)).doesNotThrowAnyException();
        verify(deadLetterService).record(eq("kanvra-activity"), eq(raw), any(Throwable.class));
        verifyNoInteractions(repository);
    }

    @Test
    void processingFailureIsRethrownNotSilentlyAcked() {
        when(repository.existsByEventId(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .thenReturn(false);
        when(repository.save(any(Activity.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> consumer().onDomainEvent(VALID_ENVELOPE))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
        // A transient failure (db down) must NOT be parked as a dead letter.
        verifyNoInteractions(deadLetterService);
    }

    @Test
    void duplicateDeliveryIsDroppedWithoutSaving() {
        when(repository.existsByEventId(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .thenReturn(true);

        assertThatCode(() -> consumer().onDomainEvent(VALID_ENVELOPE)).doesNotThrowAnyException();
        verify(repository, never()).save(any(Activity.class));
        verifyNoInteractions(deadLetterService);
    }
}