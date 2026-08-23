package dev.joaopdias.auronix.core.transaction;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.core.transaction.events.CreateTransferEvent;
import dev.joaopdias.auronix.shared.messaging.IdempotentMessageService;

@ExtendWith(MockitoExtension.class)
class TransactionConsumerTest {
    private static final UUID PAYER_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID EVENT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd16");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");

    @Mock
    private TransactionService transactionService;

    @Mock
    private IdempotentMessageService idempotentMessageService;

    private TransactionConsumer transactionConsumer;

    @BeforeEach
    void setUp() {
        transactionConsumer = new TransactionConsumer();
        ReflectionTestUtils.setField(transactionConsumer, "transactionService", transactionService);
        ReflectionTestUtils.setField(transactionConsumer, "idempotentMessageService", idempotentMessageService);
    }

    @Test
    void handleCreateTransferDelegatesThroughIdempotencyService() {
        CreateTransferEvent event = new CreateTransferEvent(EVENT_ID, PAYER_USER_ID, PAYEE_ACCOUNT_ID, 300L);
        when(idempotentMessageService.process(
            org.mockito.ArgumentMatchers.eq(EVENT_ID),
            org.mockito.ArgumentMatchers.eq("transfer.create"),
            org.mockito.ArgumentMatchers.eq(PAYEE_ACCOUNT_ID),
            org.mockito.ArgumentMatchers.any(Runnable.class)
        )).thenAnswer(invocation -> {
            Runnable action = invocation.getArgument(3);
            action.run();
            return true;
        });

        transactionConsumer.handleCreateTransfer(event);

        verify(idempotentMessageService).process(
            org.mockito.ArgumentMatchers.eq(EVENT_ID),
            org.mockito.ArgumentMatchers.eq("transfer.create"),
            org.mockito.ArgumentMatchers.eq(PAYEE_ACCOUNT_ID),
            org.mockito.ArgumentMatchers.any(Runnable.class)
        );
        verify(transactionService).transfer(event);
    }
}
