package com.kanvra.activity.repository;

import com.kanvra.activity.model.Activity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    Page<Activity> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    boolean existsByEventId(UUID eventId);
}
