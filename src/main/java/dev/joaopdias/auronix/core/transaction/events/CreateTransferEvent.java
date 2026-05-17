package dev.joaopdias.auronix.core.transaction.events;

import java.util.UUID;

public record CreateTransferEvent(
    UUID payerUserId,
    UUID payeeAccountId,
    long amount
) {
    
}
