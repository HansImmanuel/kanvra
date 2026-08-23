package com.kanvra.project.repository;

import com.kanvra.project.model.Label;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, Long> {

    List<Label> findByProjectId(Long projectId);

    /** Name-sorted list for the project-settings and task-detail pickers. */
    List<Label> findByProjectIdOrderByNameAsc(Long projectId);
}
