package dev.joaopdias.auronix.core.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponseDto(
    UUID id,
    String email,
    String name,
    Instant createdAt
) {
}