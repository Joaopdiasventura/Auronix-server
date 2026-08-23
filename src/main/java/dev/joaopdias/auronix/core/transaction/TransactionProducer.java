package dev.joaopdias.auronix.core.transaction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.transaction.events.CreateTransferEvent;
import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;
import dev.joaopdias.auronix.shared.outbox.OutboxService;

@Component
public class TransactionProducer {
    @Autowired
    private OutboxService outboxService;

    public void publishCreateTransfer(CreateTransferEvent event) {
        outboxService.enqueue(
            event.eventId(),
            "transfer.create",
            event.payeeAccountId(),
            RabbitNames.TRANSACTION_EXCHANGE,
            RabbitNames.TRANSFER_CREATE_ROUTING_KEY,
            event
        );
    }

    public void publishTransactionCompleted(TransactionCompletedEvent event) {
        outboxService.enqueue(
            event.eventId(),
            "transaction.completed",
            event.transactionId(),
            RabbitNames.TRANSACTION_EXCHANGE,
            RabbitNames.TRANSACTION_COMPLETED_ROUTING_KEY,
            event
        );
    }
}
