package dev.joaopdias.auronix.shared.messaging;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotentMessageService {
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Transactional
    public boolean process(UUID eventId, String eventType, UUID aggregateId, Runnable action) {
        int inserted = processedEventRepository.insertIfAbsent(UUID.randomUUID(), eventId, eventType, aggregateId);

        if (inserted == 0)
            return false;

        action.run();
        return true;
    }
}
