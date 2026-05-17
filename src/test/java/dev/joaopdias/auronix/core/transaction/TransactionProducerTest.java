package dev.joaopdias.auronix.core.transaction;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.transaction.events.CreateTransferEvent;
import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;

@ExtendWith(MockitoExtension.class)
class TransactionProducerTest {
    private static final UUID PAYER_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID PAYER_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd12");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");
    private static final UUID TRANSACTION_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TransactionProducer transactionProducer;

    @BeforeEach
    void setUp() {
        transactionProducer = new TransactionProducer();
        ReflectionTestUtils.setField(transactionProducer, "rabbitTemplate", rabbitTemplate);
    }

    @Test
    void publishCreateTransferUsesTransferCreateRoutingKey() {
        CreateTransferEvent event = new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 300L);

        transactionProducer.publishCreateTransfer(event);

        verify(rabbitTemplate).convertAndSend(
            RabbitNames.TRANSACTION_EXCHANGE,
            RabbitNames.TRANSFER_CREATE_ROUTING_KEY,
            event
        );
    }

    @Test
    void publishTransactionCompletedUsesTransactionCompletedRoutingKey() {
        TransactionCompletedEvent event = new TransactionCompletedEvent(
            TRANSACTION_ID,
            300L,
            PAYER_ACCOUNT_ID,
            PAYEE_ACCOUNT_ID,
            Instant.parse("2026-05-17T00:00:00Z"),
            "transaction.completed"
        );

        transactionProducer.publishTransactionCompleted(event);

        verify(rabbitTemplate).convertAndSend(
            RabbitNames.TRANSACTION_EXCHANGE,
            RabbitNames.TRANSACTION_COMPLETED_ROUTING_KEY,
            event
        );
    }
}
