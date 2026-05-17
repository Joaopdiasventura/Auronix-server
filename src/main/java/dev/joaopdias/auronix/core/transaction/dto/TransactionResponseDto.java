package dev.joaopdias.auronix.core.transaction.dto;

import java.time.Instant;
import java.util.UUID;

import dev.joaopdias.auronix.core.account.dto.AccountResponseDto;

public record TransactionResponseDto(
    UUID id,
    AccountResponseDto payer,
    AccountResponseDto payee,
    long amount,
    long payerBalanceBefore,
    long payerBalanceAfter,
    long payeeBalanceBefore,
    long payeeBalanceAfter,
    Instant createdAt
) {
}