package dev.joaopdias.auronix.core.paymentrequest;

import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.paymentrequest.events.PaymentRequestExpirationEvent;
import dev.joaopdias.auronix.shared.outbox.OutboxService;

@ExtendWith(MockitoExtension.class)
class PaymentRequestProducerTest {
    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");
    private static final UUID EVENT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd16");

    @Mock
    private OutboxService outboxService;

    private PaymentRequestProducer paymentRequestProducer;

    @BeforeEach
    void setUp() {
        paymentRequestProducer = new PaymentRequestProducer();
        ReflectionTestUtils.setField(paymentRequestProducer, "outboxService", outboxService);
    }

    @Test
    void publishExpirationUsesDelayRoutingKey() {
        PaymentRequestExpirationEvent event = new PaymentRequestExpirationEvent(EVENT_ID, PAYMENT_REQUEST_ID);

        paymentRequestProducer.publishExpiration(event);

        verify(outboxService).enqueue(
            EVENT_ID,
            "payment-request.expiration",
            PAYMENT_REQUEST_ID,
            RabbitNames.TRANSACTION_EXCHANGE,
            RabbitNames.PAYMENT_REQUEST_EXPIRATION_DELAY_ROUTING_KEY,
            event
        );
    }
}
