package com.kanvra.project.service;

import com.kanvra.common.error.ForbiddenOperationException;
import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.project.model.Project;
import com.kanvra.project.model.ProjectMember;
import com.kanvra.project.model.ProjectMemberId;
import com.kanvra.project.repository.ProjectMemberRepository;
import com.kanvra.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;

/**
 * Central project authorization (docs/TECH_DOC.md §16). Every project-scoped
 * operation goes through here so membership/ownership is always enforced
 * server-side, never relied on by the frontend.
 */
@Service
public class ProjectAccessService {

    /** MVP project roles. */
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    public ProjectAccessService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    public Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("PROJECT_NOT_FOUND", "Project not found"));
    }

    /**
     * Ensures the user belongs to the project (OWNER or MEMBER) and returns the
     * project. Throws {@link ForbiddenOperationException} otherwise.
     */
    public Project requireMembership(Long userId, Long projectId) {
        Project project = requireProject(projectId);
        if (!isMember(userId, projectId)) {
            throw new ForbiddenOperationException("You do not have access to this project");
        }
        return project;
    }

    /** Ensures the user is the project OWNER (administrative operations). */
    public void requireOwner(Long userId, Long projectId) {
        if (!isOwner(userId, projectId)) {
            throw new ForbiddenOperationException("Only the project owner can perform this action");
        }
    }

    public ProjectMember getMembership(Long userId, Long projectId) {
        return memberRepository.findByIdProjectIdAndIdUserId(projectId, userId).orElse(null);
    }

    public boolean isMember(Long userId, Long projectId) {
        return memberRepository.existsByIdProjectIdAndIdUserId(projectId, userId);
    }

    public boolean isOwner(Long userId, Long projectId) {
        return memberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
                .map(m -> ROLE_OWNER.equals(m.getRole()))
                .orElse(false);
    }

    public ProjectMemberId memberId(Long projectId, Long userId) {
        return new ProjectMemberId(projectId, userId);
    }
}
