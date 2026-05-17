package dev.joaopdias.auronix.core.transaction.dto;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponseDto(
    UUID id,
    String payerAccountEmail,
    String payeeAccountEmail,
    long amount,
    long payerBalanceBefore,
    long payerBalanceAfter,
    long payeeBalanceBefore,
    long payeeBalanceAfter,
    Instant createdAt
) {
}