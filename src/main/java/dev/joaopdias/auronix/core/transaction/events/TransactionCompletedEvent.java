package dev.joaopdias.auronix.core.transaction.events;

import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(
    UUID eventId,
    UUID transactionId,
    long amount,
    UUID payerAccountId,
    UUID payeeAccountId,
    Instant createdAt,
    String type
) {
    public TransactionCompletedEvent(
        UUID transactionId,
        long amount,
        UUID payerAccountId,
        UUID payeeAccountId,
        Instant createdAt,
        String type
    ) {
        this(UUID.randomUUID(), transactionId, amount, payerAccountId, payeeAccountId, createdAt, type);
    }
}
