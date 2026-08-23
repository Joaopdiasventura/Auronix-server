package dev.joaopdias.auronix.shared.notification;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.shared.notification.dto.RealtimeNotificationDto;
import dev.joaopdias.auronix.shared.notification.dto.TransactionNotificationDto;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RealtimeNotificationSubscriberTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID TRANSACTION_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");
    private static final UUID PAYER_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd12");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");

    @Mock
    private SseRegistryService sseRegistryService;

    private JsonMapper objectMapper;
    private RealtimeNotificationSubscriber subscriber;

    @BeforeEach
    void setUp() {
        objectMapper = new JsonMapper();
        subscriber = new RealtimeNotificationSubscriber();
        ReflectionTestUtils.setField(subscriber, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(subscriber, "sseRegistryService", sseRegistryService);
    }

    @Test
    void onMessageSendsToLocalEmittersOnly() throws Exception {
        TransactionNotificationDto notification = new TransactionNotificationDto(
            TRANSACTION_ID,
            300L,
            PAYER_ACCOUNT_ID,
            PAYEE_ACCOUNT_ID,
            Instant.parse("2026-05-17T00:00:00Z"),
            "transaction.completed"
        );
        String payload = objectMapper.writeValueAsString(new RealtimeNotificationDto(USER_ID, notification));
        Message message = new DefaultMessage(
            "auronix.realtime.notifications".getBytes(StandardCharsets.UTF_8),
            payload.getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(message, null);

        verify(sseRegistryService).send(eq(USER_ID), eq(notification));
    }
}
