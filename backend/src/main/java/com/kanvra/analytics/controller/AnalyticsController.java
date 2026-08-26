package com.kanvra.analytics.controller;

import com.kanvra.analytics.dto.ProjectAnalyticsResponse;
import com.kanvra.analytics.service.AnalyticsService;
import com.kanvra.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project analytics API (docs/SPEC.md §12.5). Project members only.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final CurrentUser currentUser;

    public AnalyticsController(AnalyticsService analyticsService, CurrentUser currentUser) {
        this.analyticsService = analyticsService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ProjectAnalyticsResponse get(@PathVariable Long projectId) {
        return analyticsService.get(currentUser.require().id(), projectId);
    }
}