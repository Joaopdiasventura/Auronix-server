package dev.joaopdias.auronix.shared.messaging;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "processed_events",
    indexes = {
        @Index(name = "idx_processed_events_event_id", columnList = "event_id"),
        @Index(name = "idx_processed_events_processed_at", columnList = "processed_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_processed_events_event_id", columnNames = "event_id")
    }
)
public class ProcessedEvent {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, name = "event_id", updatable = false)
    private UUID eventId;

    @Column(nullable = false, name = "event_type", length = 120)
    private String eventType;

    @Column(nullable = false, name = "aggregate_id")
    private UUID aggregateId;

    @Column(nullable = false, name = "processed_at")
    private Instant processedAt;

    @PrePersist
    public void prePersist() {
        if (this.processedAt == null)
            this.processedAt = Instant.now();
    }
}
