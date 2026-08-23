package dev.joaopdias.auronix.shared.notification.dto;

import java.util.UUID;

public record RealtimeNotificationDto(
    UUID userId,
    TransactionNotificationDto notification
) {
}
