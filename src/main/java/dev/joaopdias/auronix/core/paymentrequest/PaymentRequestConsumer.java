package dev.joaopdias.auronix.core.paymentrequest;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.paymentrequest.events.PaymentRequestExpirationEvent;
import dev.joaopdias.auronix.shared.messaging.IdempotentMessageService;

@Component
public class PaymentRequestConsumer {
    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private IdempotentMessageService idempotentMessageService;

    @RabbitListener(queues = RabbitNames.PAYMENT_REQUEST_EXPIRATION_QUEUE)
    public void handleExpiration(PaymentRequestExpirationEvent event) {
        idempotentMessageService.process(
            event.eventId(),
            "payment-request.expiration",
            event.paymentRequestId(),
            () -> paymentRequestService.deleteExpired(event.paymentRequestId())
        );
    }
}
