package dev.joaopdias.auronix.shared.messaging;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class IdempotentMessageService {
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Transactional
    public boolean process(UUID eventId, String eventType, UUID aggregateId, Runnable action) {
        int inserted = processedEventRepository.insertIfAbsent(UUID.randomUUID(), eventId, eventType, aggregateId);

        if (inserted == 0) {
            meterRegistry.counter("rabbitmq_duplicate_messages_total").increment();
            return false;
        }

        action.run();
        meterRegistry.counter("rabbitmq_messages_processed_total").increment();
        return true;
    }
}
