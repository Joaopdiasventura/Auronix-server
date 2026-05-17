package dev.joaopdias.auronix.core.paymentrequest;

import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.core.paymentrequest.events.PaymentRequestExpirationEvent;

@ExtendWith(MockitoExtension.class)
class PaymentRequestConsumerTest {
    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");

    @Mock
    private PaymentRequestService paymentRequestService;

    private PaymentRequestConsumer paymentRequestConsumer;

    @BeforeEach
    void setUp() {
        paymentRequestConsumer = new PaymentRequestConsumer();
        ReflectionTestUtils.setField(paymentRequestConsumer, "paymentRequestService", paymentRequestService);
    }

    @Test
    void handleExpirationDelegatesToPaymentRequestService() {
        PaymentRequestExpirationEvent event = new PaymentRequestExpirationEvent(PAYMENT_REQUEST_ID);

        paymentRequestConsumer.handleExpiration(event);

        verify(paymentRequestService).deleteExpired(PAYMENT_REQUEST_ID);
    }
}
