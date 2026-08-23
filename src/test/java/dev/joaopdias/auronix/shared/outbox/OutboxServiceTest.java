package dev.joaopdias.auronix.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {
    private static final UUID OUTBOX_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd10");
    private static final UUID EVENT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd11");
    private static final UUID AGGREGATE_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd12");

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService();
        ReflectionTestUtils.setField(outboxService, "outboxEventRepository", outboxEventRepository);
        ReflectionTestUtils.setField(outboxService, "objectMapper", new JsonMapper());
    }

    @Test
    void enqueuePersistsSerializablePendingEvent() {
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);

        outboxService.enqueue(EVENT_ID, "transfer.create", AGGREGATE_ID, "exchange", "routing", new Payload("value"));

        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEventId()).isEqualTo(EVENT_ID);
        assertThat(event.getEventType()).isEqualTo("transfer.create");
        assertThat(event.getAggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(event.getExchange()).isEqualTo("exchange");
        assertThat(event.getRoutingKey()).isEqualTo("routing");
        assertThat(event.getPayload()).isEqualTo("{\"name\":\"value\"}");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isNotNull();
    }

    @Test
    void claimPublishableMarksEventsAsProcessing() {
        OutboxEvent event = event(OutboxEventStatus.PENDING, 0);
        when(outboxEventRepository.findPublishableForUpdate(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(event));

        List<UUID> ids = outboxService.claimPublishable(10);

        assertThat(ids).containsExactly(OUTBOX_ID);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void markPublishedStoresPublishedTimestamp() {
        OutboxEvent event = event(OutboxEventStatus.PROCESSING, 1);
        when(outboxEventRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(event));

        outboxService.markPublished(OUTBOX_ID);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void markFailedIncrementsAttemptsAndSchedulesRetry() {
        OutboxEvent event = event(OutboxEventStatus.PROCESSING, 1);
        when(outboxEventRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(event));

        outboxService.markFailed(OUTBOX_ID);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
    }

    private static OutboxEvent event(OutboxEventStatus status, int attempts) {
        OutboxEvent event = new OutboxEvent();
        event.setId(OUTBOX_ID);
        event.setEventId(EVENT_ID);
        event.setEventType("transfer.create");
        event.setAggregateId(AGGREGATE_ID);
        event.setExchange("exchange");
        event.setRoutingKey("routing");
        event.setPayload("{}");
        event.setStatus(status);
        event.setAttempts(attempts);
        event.setCreatedAt(Instant.now());
        event.setNextAttemptAt(Instant.now());
        return event;
    }

    private record Payload(String name) {
    }
}
