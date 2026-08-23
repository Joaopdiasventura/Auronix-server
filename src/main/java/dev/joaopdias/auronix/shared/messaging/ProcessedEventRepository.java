package dev.joaopdias.auronix.shared.messaging;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    @Modifying
    @Query(
        value = """
            insert into processed_events (id, event_id, event_type, aggregate_id, processed_at)
            values (:id, :eventId, :eventType, :aggregateId, current_timestamp)
            on conflict (event_id) do nothing
            """,
        nativeQuery = true
    )
    int insertIfAbsent(UUID id, UUID eventId, String eventType, UUID aggregateId);
}
