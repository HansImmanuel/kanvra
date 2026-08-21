package com.kanvra.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.notification.model.Notification;
import com.kanvra.notification.repository.NotificationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationConsumer mapping + idempotency (docs/SPEC.md §12, §14). Verifies
 * recipient selection (assignee, comment author, invited user), message
 * content, the self-comment suppression rule, and the unique-constraint
 * duplicate drop.
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock private NotificationRepository repository;

    private final ObjectMapper mapper = new ObjectMapper();

    private NotificationConsumer consumer() {
        return new NotificationConsumer(repository, mapper);
    }

    private String envelope(String eventType, String payload) {
        return String.format("""
                {"eventId":"11111111-1111-1111-1111-111111111111",
                 "eventType":"%s",
                 "occurredAt":"2026-08-18T09:00:00Z",
                 "actorId":1,
                 "projectId":1,
                 "aggregateType":"task",
                 "aggregateId":5,
                 "payload":%s}
                """, eventType, payload);
    }

    @Test
    void taskAssignedCreatesNotificationForAssignee() {
        String msg = envelope("task.assigned",
                "{\"taskId\":5,\"taskTitle\":\"Fix auth\",\"assigneeId\":9,\"actorName\":\"Hans\"}");

        consumer().onDomainEvent(msg);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getRecipientId()).isEqualTo(9L);
        assertThat(n.getType()).isEqualTo("TASK_ASSIGNED");
        assertThat(n.getReferenceType()).isEqualTo("TASK");
        assertThat(n.getReferenceId()).isEqualTo(5L);
        assertThat(n.getMessage()).contains("assigned you the task 'Fix auth'");
    }

    @Test
    void taskCompletedNotifiesAssignee() {
        String msg = envelope("task.completed",
                "{\"taskId\":5,\"taskTitle\":\"Fix auth\",\"assigneeId\":9,\"actorName\":\"Hans\"}");

        consumer().onDomainEvent(msg);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("TASK_COMPLETED");
        assertThat(captor.getValue().getRecipientId()).isEqualTo(9L);
    }

    @Test
    void selfCommentDoesNotNotify() {
        String msg = envelope("comment.created",
                "{\"commentId\":7,\"taskId\":5,\"taskTitle\":\"Fix auth\",\"assigneeId\":9,"
                + "\"commentAuthorId\":9,\"actorName\":\"Hans\"}");

        consumer().onDomainEvent(msg);

        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void otherUsersCommentNotifiesAssignee() {
        String msg = envelope("comment.created",
                "{\"commentId\":7,\"taskId\":5,\"taskTitle\":\"Fix auth\",\"assigneeId\":9,"
                + "\"commentAuthorId\":3,\"actorName\":\"Jane\"}");

        consumer().onDomainEvent(msg);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getRecipientId()).isEqualTo(9L);
        assertThat(n.getType()).isEqualTo("TASK_COMMENTED");
        assertThat(n.getReferenceType()).isEqualTo("COMMENT");
        assertThat(n.getMessage()).contains("commented on your task 'Fix auth'");
    }

    @Test
    void projectInvitationNotifiesInvitedUser() {
        String msg = envelope("project.member_added",
                "{\"projectId\":1,\"userId\":4,\"role\":\"MEMBER\",\"projectName\":\"Team\",\"actorName\":\"Hans\"}");

        consumer().onDomainEvent(msg);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getRecipientId()).isEqualTo(4L);
        assertThat(n.getType()).isEqualTo("PROJECT_INVITATION");
        assertThat(n.getReferenceType()).isEqualTo("PROJECT");
        assertThat(n.getMessage()).contains("added you to project 'Team'");
    }

    @Test
    void duplicateDeliveryIsDropped() {
        String msg = envelope("task.assigned",
                "{\"taskId\":5,\"taskTitle\":\"Fix auth\",\"assigneeId\":9,\"actorName\":\"Hans\"}");
        when(repository.existsByRecipientIdAndEventId(9L,
                UUID.fromString("11111111-1111-1111-1111-111111111111"))).thenReturn(true);

        consumer().onDomainEvent(msg);

        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void irrelevantEventIsIgnored() {
        String msg = envelope("task.moved",
                "{\"taskId\":5,\"taskTitle\":\"Fix auth\"}");

        consumer().onDomainEvent(msg);

        verify(repository, never()).save(any(Notification.class));
    }
}