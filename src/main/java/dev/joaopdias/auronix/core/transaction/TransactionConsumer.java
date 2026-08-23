package dev.joaopdias.auronix.core.transaction;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.transaction.events.CreateTransferEvent;
import dev.joaopdias.auronix.shared.messaging.IdempotentMessageService;

@Component
public class TransactionConsumer {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private IdempotentMessageService idempotentMessageService;

    @RabbitListener(queues = RabbitNames.TRANSFER_CREATE_QUEUE)
    public void handleCreateTransfer(CreateTransferEvent event) {
        idempotentMessageService.process(
            event.eventId(),
            "transfer.create",
            event.payeeAccountId(),
            () -> transactionService.transfer(event)
        );
    }
}
