package dev.joaopdias.auronix.core.transaction.events;

import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(
    UUID transactionId,
    UUID payerAccountId,
    UUID payeeAccountId,
    long amount,
    long payerBalanceBefore,
    long payerBalanceAfter,
    long payeeBalanceBefore,
    long payeeBalanceAfter,
    Instant createdAt
) {
}