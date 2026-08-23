package dev.joaopdias.auronix.shared.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxService {
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(UUID eventId, String eventType, UUID aggregateId, String exchange, String routingKey, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setAggregateId(aggregateId);
        event.setExchange(exchange);
        event.setRoutingKey(routingKey);
        event.setPayload(toJson(payload));
        event.setStatus(OutboxEventStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now());
        return outboxEventRepository.save(event);
    }

    @Transactional
    public List<UUID> claimPublishable(int limit) {
        Instant now = Instant.now();
        List<OutboxEvent> events = outboxEventRepository.findPublishableForUpdate(now, PageRequest.of(0, limit));

        events.forEach(event -> {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setNextAttemptAt(now.plus(PROCESSING_TIMEOUT));
        });

        return events.stream().map(OutboxEvent::getId).toList();
    }

    @Transactional(readOnly = true)
    public OutboxEvent findById(UUID id) {
        return outboxEventRepository.findById(id).orElseThrow();
    }

    @Transactional
    public void markPublished(UUID id) {
        OutboxEvent event = outboxEventRepository.findById(id).orElseThrow();
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
    }

    @Transactional
    public void markFailed(UUID id) {
        OutboxEvent event = outboxEventRepository.findById(id).orElseThrow();
        int attempts = event.getAttempts() + 1;
        event.setStatus(OutboxEventStatus.PENDING);
        event.setAttempts(attempts);
        event.setNextAttemptAt(Instant.now().plus(backoff(attempts)));
    }

    private Duration backoff(int attempts) {
        long seconds = Math.min(300, 1L << Math.min(attempts, 8));
        return Duration.ofSeconds(seconds);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Nao foi possivel serializar evento de outbox", exception);
        }
    }
}
