package dev.joaopdias.auronix.shared.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    name = "outbox_events",
    indexes = {
        @Index(name = "idx_outbox_status_next_attempt", columnList = "status, next_attempt_at"),
        @Index(name = "idx_outbox_created_at", columnList = "created_at"),
        @Index(name = "idx_outbox_event_id", columnList = "event_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_outbox_event_id", columnNames = "event_id")
    }
)
public class OutboxEvent {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, name = "event_id", updatable = false)
    private UUID eventId;

    @Column(nullable = false, name = "event_type", length = 120)
    private String eventType;

    @Column(nullable = false, name = "aggregate_id")
    private UUID aggregateId;

    @Column(nullable = false, length = 180)
    private String exchange;

    @Column(nullable = false, name = "routing_key", length = 180)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false, name = "next_attempt_at")
    private Instant nextAttemptAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();

        if (this.eventId == null)
            this.eventId = UUID.randomUUID();

        if (this.status == null)
            this.status = OutboxEventStatus.PENDING;

        if (this.createdAt == null)
            this.createdAt = now;

        if (this.nextAttemptAt == null)
            this.nextAttemptAt = now;
    }
}
