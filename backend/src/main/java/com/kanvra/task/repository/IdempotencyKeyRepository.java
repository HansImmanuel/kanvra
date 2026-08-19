package com.kanvra.task.repository;

import com.kanvra.task.model.IdempotencyKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndIdempotencyKeyAndResourceType(Long userId, String key, String resourceType);
}
