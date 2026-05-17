package dev.joaopdias.auronix.shared.notification;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.core.account.AccountRepository;
import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;
import dev.joaopdias.auronix.shared.notification.dto.TransactionNotificationDto;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    private static final UUID PAYER_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID PAYEE_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd11");
    private static final UUID OTHER_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd15");
    private static final UUID PAYER_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd12");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");
    private static final UUID TRANSACTION_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");
    private static final Instant CREATED_AT = Instant.parse("2026-05-17T00:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SseRegistryService sseRegistryService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
        ReflectionTestUtils.setField(notificationService, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(notificationService, "sseRegistryService", sseRegistryService);
    }

    @Test
    void notifyTransactionCompletedSendsOnlyToPayerAndPayee() {
        when(accountRepository.findUserIdById(PAYER_ACCOUNT_ID)).thenReturn(Optional.of(PAYER_USER_ID));
        when(accountRepository.findUserIdById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.of(PAYEE_USER_ID));

        notificationService.notifyTransactionCompleted(event());

        verify(sseRegistryService).send(eq(PAYER_USER_ID), notificationMatcher());
        verify(sseRegistryService).send(eq(PAYEE_USER_ID), notificationMatcher());
        verify(sseRegistryService, never()).send(org.mockito.ArgumentMatchers.eq(OTHER_USER_ID), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notifyTransactionCompletedIgnoresMissingAccounts() {
        when(accountRepository.findUserIdById(PAYER_ACCOUNT_ID)).thenReturn(Optional.empty());
        when(accountRepository.findUserIdById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.empty());

        notificationService.notifyTransactionCompleted(event());

        verify(sseRegistryService, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static TransactionNotificationDto notificationMatcher() {
        return argThat(notification -> notification.transactionId().equals(TRANSACTION_ID)
            && notification.amount() == 300L
            && notification.payerAccountId().equals(PAYER_ACCOUNT_ID)
            && notification.payeeAccountId().equals(PAYEE_ACCOUNT_ID)
            && notification.createdAt().equals(CREATED_AT)
            && notification.type().equals("transaction.completed"));
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
