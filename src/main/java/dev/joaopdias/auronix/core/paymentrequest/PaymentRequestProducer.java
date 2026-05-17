package dev.joaopdias.auronix.core.paymentrequest;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.paymentrequest.events.PaymentRequestExpirationEvent;

@Component
public class PaymentRequestProducer {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publishExpiration(PaymentRequestExpirationEvent event) {
        rabbitTemplate.convertAndSend(
            RabbitNames.TRANSACTION_EXCHANGE,
            RabbitNames.PAYMENT_REQUEST_EXPIRATION_DELAY_ROUTING_KEY,
            event
        );
    }
}
