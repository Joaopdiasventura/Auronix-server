package dev.joaopdias.auronix.shared.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record SseConnectionMetadata(
    UUID userId,
    UUID connectionId,
    Instant connectedAt,
    Instant expiresAt,
    String instanceId
) {
}
