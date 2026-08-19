package com.kanvra.project.dto;

import com.kanvra.project.model.ProjectMember;
import java.time.Instant;

/**
 * A project member + user identity, used for the member list and assignee
 * dropdown (docs/SPEC.md §4).
 */
public record MemberResponse(Long id, String name, String role, Instant joinedAt) {

    public static MemberResponse from(ProjectMember member, String name) {
        return new MemberResponse(member.getId().getUserId(), name, member.getRole(), member.getJoinedAt());
    }
}
