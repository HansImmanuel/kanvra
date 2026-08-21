package com.kanvra.notification.service;

import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.notification.dto.NotificationResponse;
import com.kanvra.notification.model.Notification;
import com.kanvra.notification.repository.NotificationRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification read endpoints (docs/SPEC.md §12). Rows are written by the
 * {@code kanvra-notification} consumer (NotificationConsumer); this service
 * only reads them and manages read-state. All access is scoped to the current
 * user — nobody can read or mark someone else's notifications.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found"));
        // A user may only mark their own notifications read.
        if (!notification.getRecipientId().equals(userId)) {
            throw new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }
}