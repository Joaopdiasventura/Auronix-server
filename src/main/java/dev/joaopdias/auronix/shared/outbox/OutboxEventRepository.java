package dev.joaopdias.auronix.shared.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(
        value = """
            select *
            from outbox_events
            where status in ('PENDING', 'PROCESSING')
              and next_attempt_at <= :now
            order by created_at
            for update skip locked
            """,
        nativeQuery = true
    )
    List<OutboxEvent> findPublishableForUpdate(Instant now, Pageable pageable);
}
