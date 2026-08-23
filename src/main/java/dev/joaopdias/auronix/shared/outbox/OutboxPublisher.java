package dev.joaopdias.auronix.shared.outbox;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    @Autowired
    private OutboxService outboxService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.publish-delay-ms:1000}")
    public void publishPending() {
        outboxService.claimPublishable(batchSize).forEach(this::publish);
    }

    private void publish(UUID id) {
        try {
            OutboxEvent event = outboxService.findById(id);
            rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), message(event));
            outboxService.markPublished(id);
            meterRegistry.counter("outbox_published_total").increment();
        } catch (RuntimeException exception) {
            outboxService.markFailed(id);
            meterRegistry.counter("outbox_publish_failures_total").increment();
        }
    }

    private Message message(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(event.getEventId().toString());
        properties.setHeader("eventId", event.getEventId().toString());
        properties.setHeader("eventType", event.getEventType());
        properties.setHeader("aggregateId", event.getAggregateId().toString());
        return new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);
    }
}
