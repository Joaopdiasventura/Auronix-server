package dev.joaopdias.auronix.core.account.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountResponseDto(
    UUID id,
    String email,
    String name,
    Instant createdAt,
    long balance
) {
    
}
