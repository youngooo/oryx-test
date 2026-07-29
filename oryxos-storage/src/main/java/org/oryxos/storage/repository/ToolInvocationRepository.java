package org.oryxos.storage.repository;

import java.util.List;
import org.oryxos.storage.entity.ToolInvocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ToolInvocationRepository
        extends JpaRepository<ToolInvocationEntity, String> {
    @Query(value = """
            SELECT * FROM tool_invocations
            WHERE session_id = :sessionId
            ORDER BY started_at ASC, id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ToolInvocationEntity> findPageBySessionId(
            @Param("sessionId") String sessionId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countBySessionId(String sessionId);
}
