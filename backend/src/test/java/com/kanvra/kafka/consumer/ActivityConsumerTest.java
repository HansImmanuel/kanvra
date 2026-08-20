package com.kanvra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.activity.model.Activity;
import com.kanvra.activity.repository.ActivityRepository;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ActivityConsumer failure semantics (docs/SPEC.md §14, AGENT.md §12): malformed
 * or unexpected events must NOT be silently acked (Kafka should redeliver);
 * only genuine duplicate delivery (unique events.event_id already present) is
 * a silent no-op. This is the retry-safe pattern the notification/realtime
 * consumers will follow in later sprints.
 */
@ExtendWith(MockitoExtension.class)
class ActivityConsumerTest {

    @Mock private ActivityRepository repository;

    private final ObjectMapper mapper = new ObjectMapper();

    private ActivityConsumer consumer() {
        return new ActivityConsumer(repository, mapper);
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
    void malformedEnvelopeIsRethrownForKafkaRedelivery() {
        ActivityConsumer consumer = consumer();
        assertThatThrownBy(() -> consumer.onDomainEvent("this is not JSON {"))
                .isInstanceOf(UncheckedIOException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void processingFailureIsRethrownNotSilentlyAcked() {
        when(repository.existsByEventId(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .thenReturn(false);
        when(repository.save(any(Activity.class))).thenThrow(new RuntimeException("db down"));

        ActivityConsumer consumer = consumer();
        assertThatThrownBy(() -> consumer.onDomainEvent(VALID_ENVELOPE))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
    }

    @Test
    void duplicateDeliveryIsDroppedWithoutSaving() {
        when(repository.existsByEventId(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .thenReturn(true);

        ActivityConsumer consumer = consumer();
        assertThatCode(() -> consumer.onDomainEvent(VALID_ENVELOPE)).doesNotThrowAnyException();
        verify(repository, never()).save(any(Activity.class));
    }
}