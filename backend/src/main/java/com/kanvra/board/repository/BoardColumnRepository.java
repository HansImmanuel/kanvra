package com.kanvra.board.repository;

import com.kanvra.board.model.BoardColumn;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findByBoardIdOrderByPositionAsc(Long boardId);

    /**
     * Locks every column of a board for a reorder. Rows are locked in canonical
     * {@code id} order so two concurrent {@code reorderColumns} calls acquire the
     * same lock sequence and cannot deadlock (mirrors the cross-column task-move
     * strategy in TaskService.move(), SPEC.md §6/§7).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BoardColumn c where c.boardId = :boardId order by c.id asc")
    List<BoardColumn> lockColumnsByBoardId(@Param("boardId") Long boardId);
}
