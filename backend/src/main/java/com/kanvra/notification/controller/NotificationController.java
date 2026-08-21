package com.kanvra.notification.controller;

import com.kanvra.common.api.ApiPage;
import com.kanvra.common.security.CurrentUser;
import com.kanvra.notification.dto.NotificationResponse;
import com.kanvra.notification.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification API (docs/SPEC.md §12): list, mark one read, mark all read.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiPage<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ApiPage.from(notificationService.list(currentUser.require().id(), PageRequest.of(page, size)));
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable Long notificationId) {
        return notificationService.markRead(currentUser.require().id(), notificationId);
    }

    @PostMapping("/read-all")
    public void markAllRead() {
        notificationService.markAllRead(currentUser.require().id());
    }
}