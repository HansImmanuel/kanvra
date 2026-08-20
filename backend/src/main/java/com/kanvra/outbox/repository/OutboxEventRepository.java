package com.kanvra.outbox.repository;

import com.kanvra.outbox.model.OutboxEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Bounded poll for the scheduled publisher: only the oldest 100 unpublished
     * rows are loaded per tick so a large backlog cannot be pulled into memory
     * in one shot, and the synchronous {@code KafkaTemplate.send(...).get(...)}
     * loop cannot spin unboundedly against a down broker (docs/TECH_DOC.md §8).
     */
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByIdAsc();

    @Modifying
    @Query("update OutboxEvent o set o.publishedAt = :now where o.id in :ids")
    int markPublished(@Param("ids") List<Long> ids, @Param("now") Instant now);
}
