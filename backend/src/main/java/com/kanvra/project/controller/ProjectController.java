package com.kanvra.project.controller;

import com.kanvra.common.api.ApiPage;
import com.kanvra.common.security.CurrentUser;
import com.kanvra.project.dto.AddMemberRequest;
import com.kanvra.project.dto.MemberResponse;
import com.kanvra.project.dto.ProjectRequest;
import com.kanvra.project.dto.ProjectResponse;
import com.kanvra.project.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project API (docs/SPEC.md §4).
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public ProjectController(ProjectService projectService, CurrentUser currentUser) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
        return projectService.create(currentUser.require().id(), request);
    }

    @GetMapping
    public ApiPage<ProjectResponse> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ApiPage.from(projectService.list(currentUser.require().id(), PageRequest.of(page, size)));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable Long projectId) {
        return projectService.get(currentUser.require().id(), projectId);
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(@PathVariable Long projectId, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(currentUser.require().id(), projectId, request);
    }

    @PostMapping("/{projectId}/archive")
    public ProjectResponse archive(@PathVariable Long projectId) {
        return projectService.archive(currentUser.require().id(), projectId);
    }

    @PostMapping("/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@PathVariable Long projectId, @Valid @RequestBody AddMemberRequest request) {
        return projectService.addMember(currentUser.require().id(), projectId, request);
    }

    @GetMapping("/{projectId}/members")
    public ApiPage<MemberResponse> listMembers(@PathVariable Long projectId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        List<MemberResponse> members = projectService.listMembers(currentUser.require().id(), projectId);
        return ApiPage.from(PageRequest.of(page, size), members);
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectService.removeMember(currentUser.require().id(), projectId, userId);
    }
}
