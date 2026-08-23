package dev.joaopdias.auronix.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    private static final UUID OUTBOX_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd10");
    private static final UUID EVENT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd11");
    private static final UUID AGGREGATE_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd12");

    @Mock
    private OutboxService outboxService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisher outboxPublisher;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        outboxPublisher = new OutboxPublisher();
        ReflectionTestUtils.setField(outboxPublisher, "outboxService", outboxService);
        ReflectionTestUtils.setField(outboxPublisher, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(outboxPublisher, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 10);
    }

    @Test
    void publishPendingSendsClaimedEventsAndMarksPublished() {
        when(outboxService.claimPublishable(10)).thenReturn(List.of(OUTBOX_ID));
        when(outboxService.findById(OUTBOX_ID)).thenReturn(event());
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        outboxPublisher.publishPending();

        verify(rabbitTemplate).send(eq("exchange"), eq("routing"), messageCaptor.capture());
        verify(outboxService).markPublished(OUTBOX_ID);
        Message message = messageCaptor.getValue();
        assertThat(new String(message.getBody())).isEqualTo("{\"ok\":true}");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(EVENT_ID.toString());
        assertThat((String) message.getMessageProperties().getHeader("eventId")).isEqualTo(EVENT_ID.toString());
        assertThat((String) message.getMessageProperties().getHeader("eventType")).isEqualTo("transfer.create");
        assertThat((String) message.getMessageProperties().getHeader("aggregateId")).isEqualTo(AGGREGATE_ID.toString());
        assertThat(meterRegistry.counter("outbox_published_total").count()).isEqualTo(1);
    }

    @Test
    void publishPendingMarksFailedWhenRabbitSendFails() {
        when(outboxService.claimPublishable(10)).thenReturn(List.of(OUTBOX_ID));
        when(outboxService.findById(OUTBOX_ID)).thenReturn(event());
        doThrow(new RuntimeException("rabbit unavailable"))
            .when(rabbitTemplate)
            .send(eq("exchange"), eq("routing"), any(Message.class));

        outboxPublisher.publishPending();

        verify(outboxService).markFailed(OUTBOX_ID);
        assertThat(meterRegistry.counter("outbox_publish_failures_total").count()).isEqualTo(1);
    }

    private static OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setId(OUTBOX_ID);
        event.setEventId(EVENT_ID);
        event.setEventType("transfer.create");
        event.setAggregateId(AGGREGATE_ID);
        event.setExchange("exchange");
        event.setRoutingKey("routing");
        event.setPayload("{\"ok\":true}");
        event.setStatus(OutboxEventStatus.PROCESSING);
        event.setAttempts(0);
        event.setCreatedAt(Instant.now());
        event.setNextAttemptAt(Instant.now());
        return event;
    }
}
