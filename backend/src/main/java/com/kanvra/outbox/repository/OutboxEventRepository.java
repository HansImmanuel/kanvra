package com.kanvra.outbox.repository;

import com.kanvra.outbox.model.OutboxEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("select o from OutboxEvent o where o.publishedAt is null order by o.id asc")
    List<OutboxEvent> findUnpublished();

    @Modifying
    @Query("update OutboxEvent o set o.publishedAt = :now where o.id in :ids")
    int markPublished(@Param("ids") List<Long> ids, @Param("now") Instant now);
}
