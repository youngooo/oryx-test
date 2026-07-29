package org.oryxos.storage.repository;

import java.util.List;
import org.oryxos.storage.entity.LlmCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmCallRepository extends JpaRepository<LlmCallEntity, String> {
    @Query(value = """
            SELECT * FROM llm_calls
            WHERE session_id = :sessionId
            ORDER BY started_at ASC, id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<LlmCallEntity> findPageBySessionId(
            @Param("sessionId") String sessionId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countBySessionId(String sessionId);
}
