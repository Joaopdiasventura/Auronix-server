package dev.joaopdias.auronix.core.transaction;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.transaction.events.CreateTransferEvent;

@Component
public class TransactionConsumer {

    @Autowired
    private TransactionService transactionService;

    @RabbitListener(queues = RabbitNames.TRANSFER_CREATE_QUEUE)
    public void handleCreateTransfer(CreateTransferEvent event) {
        transactionService.transfer(event);
    }
}