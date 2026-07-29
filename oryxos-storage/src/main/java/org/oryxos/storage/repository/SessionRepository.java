package org.oryxos.storage.repository;

import java.util.Optional;
import org.oryxos.storage.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {
    Optional<SessionEntity> findFirstByProfileNameAndChannelAndUserIdAndStatusOrderByLastActiveAtDesc(
            String profileName, String channel, String userId, String status);
    List<SessionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
