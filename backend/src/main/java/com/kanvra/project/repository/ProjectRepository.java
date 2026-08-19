package com.kanvra.project.repository;

import com.kanvra.project.model.Project;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Projects accessible to a user: those they own or are a member of.
     */
    @Query("select p from Project p "
            + "where p.ownerId = :userId or exists (select 1 from ProjectMember pm "
            + "where pm.id.projectId = p.id and pm.id.userId = :userId)")
    Page<Project> findAccessible(@Param("userId") Long userId, Pageable pageable);
}

