package dev.joaopdias.auronix.core.paymentrequest.dto;

import java.time.Instant;
import java.util.UUID;

import dev.joaopdias.auronix.core.account.dto.AccountResponseDto;

public record PaymentRequestResponseDto(
    UUID id,
    long value,
    AccountResponseDto account,
    Instant createdAt
) {
}
