package dev.joaopdias.auronix.core.paymentrequest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.core.paymentrequest.events.PaymentRequestExpirationEvent;
import dev.joaopdias.auronix.shared.messaging.IdempotentMessageService;

@ExtendWith(MockitoExtension.class)
class PaymentRequestConsumerTest {
    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");
    private static final UUID EVENT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd16");

    @Mock
    private PaymentRequestService paymentRequestService;

    @Mock
    private IdempotentMessageService idempotentMessageService;

    private PaymentRequestConsumer paymentRequestConsumer;

    @BeforeEach
    void setUp() {
        paymentRequestConsumer = new PaymentRequestConsumer();
        ReflectionTestUtils.setField(paymentRequestConsumer, "paymentRequestService", paymentRequestService);
        ReflectionTestUtils.setField(paymentRequestConsumer, "idempotentMessageService", idempotentMessageService);
    }

    @Test
    void handleExpirationDelegatesThroughIdempotencyService() {
        PaymentRequestExpirationEvent event = new PaymentRequestExpirationEvent(EVENT_ID, PAYMENT_REQUEST_ID);
        when(idempotentMessageService.process(
            org.mockito.ArgumentMatchers.eq(EVENT_ID),
            org.mockito.ArgumentMatchers.eq("payment-request.expiration"),
            org.mockito.ArgumentMatchers.eq(PAYMENT_REQUEST_ID),
            org.mockito.ArgumentMatchers.any(Runnable.class)
        )).thenAnswer(invocation -> {
            Runnable action = invocation.getArgument(3);
            action.run();
            return true;
        });

        paymentRequestConsumer.handleExpiration(event);

        verify(idempotentMessageService).process(
            org.mockito.ArgumentMatchers.eq(EVENT_ID),
            org.mockito.ArgumentMatchers.eq("payment-request.expiration"),
            org.mockito.ArgumentMatchers.eq(PAYMENT_REQUEST_ID),
            org.mockito.ArgumentMatchers.any(Runnable.class)
        );
        verify(paymentRequestService).deleteExpired(PAYMENT_REQUEST_ID);
    }
}
