package com.kanvra.activity.controller;

import com.kanvra.activity.dto.ActivityResponse;
import com.kanvra.activity.service.ActivityService;
import com.kanvra.common.api.ApiPage;
import com.kanvra.common.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Activity feed API (docs/SPEC.md §11).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/activity")
public class ActivityController {

    private final ActivityService activityService;
    private final CurrentUser currentUser;

    public ActivityController(ActivityService activityService, CurrentUser currentUser) {
        this.activityService = activityService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiPage<ActivityResponse> list(@PathVariable Long projectId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return ApiPage.from(activityService.list(currentUser.require().id(), projectId, PageRequest.of(page, size)));
    }
}
