package dev.joaopdias.auronix.core.transaction;

import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.core.transaction.events.CreateTransferEvent;

@ExtendWith(MockitoExtension.class)
class TransactionConsumerTest {
    private static final UUID PAYER_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");

    @Mock
    private TransactionService transactionService;

    private TransactionConsumer transactionConsumer;

    @BeforeEach
    void setUp() {
        transactionConsumer = new TransactionConsumer();
        ReflectionTestUtils.setField(transactionConsumer, "transactionService", transactionService);
    }

    @Test
    void handleCreateTransferDelegatesToTransactionService() {
        CreateTransferEvent event = new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 300L);

        transactionConsumer.handleCreateTransfer(event);

        verify(transactionService).transfer(event);
    }
}
