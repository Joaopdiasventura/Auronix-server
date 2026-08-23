package dev.joaopdias.auronix.shared.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.core.account.AccountRepository;
import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;
import dev.joaopdias.auronix.shared.notification.dto.RealtimeNotificationDto;
import dev.joaopdias.auronix.shared.notification.dto.TransactionNotificationDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    private static final UUID PAYER_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID PAYEE_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd11");
    private static final UUID PAYER_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd12");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");
    private static final UUID TRANSACTION_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");
    private static final Instant CREATED_AT = Instant.parse("2026-05-17T00:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    private NotificationService notificationService;
    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new JsonMapper();
        notificationService = new NotificationService();
        ReflectionTestUtils.setField(notificationService, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(notificationService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(notificationService, "realtimeNotificationsTopic", new ChannelTopic("test.realtime"));
        ReflectionTestUtils.setField(notificationService, "objectMapper", objectMapper);
    }

    @Test
    void notifyTransactionCompletedPublishesRealtimeEventsForPayerAndPayee() throws Exception {
        when(accountRepository.findUserIdById(PAYER_ACCOUNT_ID)).thenReturn(Optional.of(PAYER_USER_ID));
        when(accountRepository.findUserIdById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.of(PAYEE_USER_ID));
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.notifyTransactionCompleted(event());

        verify(redisTemplate, times(2)).convertAndSend(eq("test.realtime"), payloadCaptor.capture());
        List<RealtimeNotificationDto> notifications = payloadCaptor.getAllValues()
            .stream()
            .map(this::readRealtimeNotification)
            .toList();

        assertThat(notifications)
            .extracting(RealtimeNotificationDto::userId)
            .containsExactly(PAYER_USER_ID, PAYEE_USER_ID);

        assertThat(notifications)
            .allSatisfy(realtimeNotification -> assertNotification(realtimeNotification.notification()));
    }

    @Test
    void notifyTransactionCompletedIgnoresMissingAccounts() {
        when(accountRepository.findUserIdById(PAYER_ACCOUNT_ID)).thenReturn(Optional.empty());
        when(accountRepository.findUserIdById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.empty());

        notificationService.notifyTransactionCompleted(event());

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    private RealtimeNotificationDto readRealtimeNotification(String payload) {
        try {
            return objectMapper.readValue(payload, RealtimeNotificationDto.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertNotification(TransactionNotificationDto notification) {
        assertThat(notification.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(notification.amount()).isEqualTo(300L);
        assertThat(notification.payerAccountId()).isEqualTo(PAYER_ACCOUNT_ID);
        assertThat(notification.payeeAccountId()).isEqualTo(PAYEE_ACCOUNT_ID);
        assertThat(notification.createdAt()).isEqualTo(CREATED_AT);
        assertThat(notification.type()).isEqualTo("transaction.completed");
    }

    private static TransactionCompletedEvent event() {
        return new TransactionCompletedEvent(
            TRANSACTION_ID,
            300L,
            PAYER_ACCOUNT_ID,
            PAYEE_ACCOUNT_ID,
            CREATED_AT,
            "transaction.completed"
        );
    }
}
