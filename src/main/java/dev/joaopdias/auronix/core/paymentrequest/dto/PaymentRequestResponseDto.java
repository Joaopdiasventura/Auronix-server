package dev.joaopdias.auronix.core.paymentrequest.dto;

import java.time.Instant;
import java.util.UUID;

import dev.joaopdias.auronix.core.user.dto.UserResponseDto;

public record PaymentRequestResponseDto(
    UUID id,
    long value,
    UserResponseDto user,
    Instant createdAt
) {
}
