package dev.joaopdias.auronix.shared.notification;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {
    private static final UUID TRANSACTION_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");
    private static final UUID PAYER_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd12");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");

    @Mock
    private NotificationService notificationService;

    private NotificationConsumer notificationConsumer;

    @BeforeEach
    void setUp() {
        notificationConsumer = new NotificationConsumer();
        ReflectionTestUtils.setField(notificationConsumer, "notificationService", notificationService);
    }

    @Test
    void handleTransactionCompletedDelegatesToNotificationService() {
        TransactionCompletedEvent event = event();

        notificationConsumer.handleTransactionCompleted(event);

        verify(notificationService).notifyTransactionCompleted(event);
    }

    private static TransactionCompletedEvent event() {
        return new TransactionCompletedEvent(
            TRANSACTION_ID,
            300L,
            PAYER_ACCOUNT_ID,
            PAYEE_ACCOUNT_ID,
            Instant.parse("2026-05-17T00:00:00Z"),
            "transaction.completed"
        );
    }
}
