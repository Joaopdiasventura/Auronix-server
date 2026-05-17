package dev.joaopdias.auronix.shared.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record TransactionNotificationDto(
    UUID transactionId,
    long amount,
    UUID payerAccountId,
    UUID payeeAccountId,
    Instant createdAt,
    String type
) {
}
