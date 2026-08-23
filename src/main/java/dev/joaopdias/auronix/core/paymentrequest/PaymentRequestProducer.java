package dev.joaopdias.auronix.core.paymentrequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.paymentrequest.events.PaymentRequestExpirationEvent;
import dev.joaopdias.auronix.shared.outbox.OutboxService;

@Component
public class PaymentRequestProducer {
    @Autowired
    private OutboxService outboxService;

    public void publishExpiration(PaymentRequestExpirationEvent event) {
        outboxService.enqueue(
            event.eventId(),
            "payment-request.expiration",
            event.paymentRequestId(),
            RabbitNames.TRANSACTION_EXCHANGE,
            RabbitNames.PAYMENT_REQUEST_EXPIRATION_DELAY_ROUTING_KEY,
            event
        );
    }
}
