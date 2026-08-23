package dev.joaopdias.auronix.core.transaction.events;

import java.util.UUID;

public record CreateTransferEvent(
    UUID eventId,
    UUID payerUserId,
    UUID payeeAccountId,
    long amount
) {
    public CreateTransferEvent(UUID payerUserId, UUID payeeAccountId, long amount) {
        this(UUID.randomUUID(), payerUserId, payeeAccountId, amount);
    }
}
