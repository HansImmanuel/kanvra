package com.kanvra.project.controller;

import com.kanvra.common.security.CurrentUser;
import com.kanvra.project.dto.LabelRequest;
import com.kanvra.project.dto.LabelResponse;
import com.kanvra.project.service.LabelService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Label API (docs/SPEC.md §9).
 */
@RestController
@RequestMapping("/api/v1")
public class LabelController {

    private final LabelService labelService;
    private final CurrentUser currentUser;

    public LabelController(LabelService labelService, CurrentUser currentUser) {
        this.labelService = labelService;
        this.currentUser = currentUser;
    }

    @GetMapping("/projects/{projectId}/labels")
    public List<LabelResponse> list(@PathVariable Long projectId) {
        return labelService.list(currentUser.require().id(), projectId);
    }

    @PostMapping("/projects/{projectId}/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public LabelResponse create(@PathVariable Long projectId, @Valid @RequestBody LabelRequest request) {
        return labelService.create(currentUser.require().id(), projectId, request);
    }

    @PatchMapping("/labels/{labelId}")
    public LabelResponse update(@PathVariable Long labelId, @Valid @RequestBody LabelRequest request) {
        return labelService.update(currentUser.require().id(), labelId, request);
    }

    @DeleteMapping("/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long labelId) {
        labelService.delete(currentUser.require().id(), labelId);
    }
}
