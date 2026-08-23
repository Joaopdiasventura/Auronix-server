package dev.joaopdias.auronix.core.paymentrequest.events;

import java.util.UUID;

public record PaymentRequestExpirationEvent(
    UUID eventId,
    UUID paymentRequestId
) {
    public PaymentRequestExpirationEvent(UUID paymentRequestId) {
        this(UUID.randomUUID(), paymentRequestId);
    }
}
