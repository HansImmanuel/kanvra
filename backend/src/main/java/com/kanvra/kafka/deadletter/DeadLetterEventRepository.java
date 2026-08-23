package com.kanvra.kafka.deadletter;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, Long> {

    /** Most-recent dead letters for manual inspection. */
    List<DeadLetterEvent> findTop100ByOrderByIdDesc();

    /** Retention purge; requires a surrounding transaction. */
    int deleteByCreatedAtBefore(Instant cutoff);
}