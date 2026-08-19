package com.kanvra.project.repository;

import com.kanvra.project.model.ProjectMember;
import com.kanvra.project.model.ProjectMemberId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    Optional<ProjectMember> findByIdProjectIdAndIdUserId(Long projectId, Long userId);

    List<ProjectMember> findByIdProjectId(Long projectId);

    Page<ProjectMember> findByIdProjectId(Long projectId, Pageable pageable);

    boolean existsByIdProjectIdAndIdUserId(Long projectId, Long userId);
}
