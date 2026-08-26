package com.kanvra.analytics.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.analytics.model.AnalyticsEvent;
import com.kanvra.analytics.model.ProjectAnalytics;
import com.kanvra.analytics.repository.AnalyticsEventRepository;
import com.kanvra.analytics.repository.ProjectAnalyticsRepository;
import com.kanvra.kafka.deadletter.DeadLetterService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AnalyticsConsumer semantics (docs/SPEC.md §12.5/§14, AGENT.md §12,
 * TECH_DOC.md §20): tracked events increment the right per-project counter and
 * write the idempotency ledger row; duplicate delivery is dropped; malformed or
 * structurally broken messages are parked in the dead-letter table and acked;
 * transient failures are rethrown so Kafka redelivers.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsConsumerTest {

    @Mock private ProjectAnalyticsRepository analyticsRepository;
    @Mock private AnalyticsEventRepository eventRepository;
    @Mock private DeadLetterService deadLetterService;

    private final ObjectMapper mapper = new ObjectMapper();

    private AnalyticsConsumer consumer() {
        return new AnalyticsConsumer(analyticsRepository, eventRepository, mapper, deadLetterService);
    }

    private static String envelope(String eventType, String eventId) {
        return "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"" + eventType + "\","
                + "\"occurredAt\":\"2026-08-18T09:00:00Z\","
                + "\"actorId\":1,"
                + "\"projectId\":1,"
                + "\"aggregateType\":\"task\","
                + "\"aggregateId\":5,"
                + "\"payload\":{\"taskId\":5,\"taskTitle\":\"T\",\"columnId\":1,\"actorName\":\"Hans\"}"
                + "}";
    }

    private static final String UUID_1 = "11111111-1111-1111-1111-111111111111";

    @Test
    void malformedEnvelopeIsParkedInDeadLetterTableNotAckedSilently() {
        String raw = "this is not JSON {";
        assertThatCode(() -> consumer().onDomainEvent(raw)).doesNotThrowAnyException();
        verify(deadLetterService).record(eq("kanvra-analytics"), eq(raw), any(Throwable.class));
        verifyNoInteractions(analyticsRepository, eventRepository);
    }

    @Test
    void structurallyBrokenEnvelopeIsParkedInDeadLetterTable() {
        String raw = "{\"eventType\":\"task.created\",\"payload\":{\"taskTitle\":\"No event id\"}}";
        assertThatCode(() -> consumer().onDomainEvent(raw)).doesNotThrowAnyException();
        verify(deadLetterService).record(eq("kanvra-analytics"), eq(raw), any(Throwable.class));
        verifyNoInteractions(analyticsRepository, eventRepository);
    }

    @Test
    void processingFailureIsRethrownNotSilentlyAcked() {
        when(eventRepository.existsByEventId(UUID.fromString(UUID_1))).thenReturn(false);
        when(analyticsRepository.findById(1L)).thenReturn(Optional.empty());
        when(analyticsRepository.save(any(ProjectAnalytics.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> consumer().onDomainEvent(envelope("task.created", UUID_1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
        // A transient failure (db down) must NOT be parked as a dead letter.
        verifyNoInteractions(deadLetterService);
    }

    @Test
    void duplicateDeliveryIsDroppedWithoutSaving() {
        when(eventRepository.existsByEventId(UUID.fromString(UUID_1))).thenReturn(true);

        assertThatCode(() -> consumer().onDomainEvent(envelope("task.created", UUID_1))).doesNotThrowAnyException();
        verify(analyticsRepository, never()).save(any(ProjectAnalytics.class));
        verify(eventRepository, never()).save(any(AnalyticsEvent.class));
        verifyNoInteractions(deadLetterService);
    }

    @Test
    void taskCreatedIncrementsCounterAndWritesLedger() {
        when(eventRepository.existsByEventId(UUID.fromString(UUID_1))).thenReturn(false);
        when(analyticsRepository.findById(1L)).thenReturn(Optional.empty());

        consumer().onDomainEvent(envelope("task.created", UUID_1));

        ArgumentCaptor<ProjectAnalytics> row = ArgumentCaptor.forClass(ProjectAnalytics.class);
        verify(analyticsRepository).save(row.capture());
        assertThat(row.getValue().getProjectId()).isEqualTo(1L);
        assertThat(row.getValue().getTasksCreated()).isEqualTo(1);
        assertThat(row.getValue().getTasksDeleted()).isZero();

        ArgumentCaptor<AnalyticsEvent> ledger = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(ledger.capture());
        assertThat(ledger.getValue().getEventId()).isEqualTo(UUID.fromString(UUID_1));
        assertThat(ledger.getValue().getProjectId()).isEqualTo(1L);
    }

    @Test
    void completedEventIncrementsItsOwnCounter() {
        when(eventRepository.existsByEventId(UUID.fromString(UUID_1))).thenReturn(false);
        when(analyticsRepository.findById(1L)).thenReturn(Optional.empty());

        consumer().onDomainEvent(envelope("task.completed", UUID_1));

        ArgumentCaptor<ProjectAnalytics> row = ArgumentCaptor.forClass(ProjectAnalytics.class);
        verify(analyticsRepository).save(row.capture());
        assertThat(row.getValue().getTasksCompleted()).isEqualTo(1);
    }

    @Test
    void untrackedEventIsIgnored() {
        assertThatCode(() -> consumer().onDomainEvent(envelope("project.created", UUID_1))).doesNotThrowAnyException();
        verifyNoInteractions(analyticsRepository, eventRepository, deadLetterService);
    }

    @Test
    void missingProjectIdIsIgnored() {
        String raw = "{\"eventId\":\"" + UUID_1 + "\",\"eventType\":\"task.created\","
                + "\"payload\":{\"taskId\":5}}"; // no projectId
        assertThatCode(() -> consumer().onDomainEvent(raw)).doesNotThrowAnyException();
        verifyNoInteractions(analyticsRepository, eventRepository, deadLetterService);
    }
}