package dev.joaopdias.auronix.core.transaction;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.transaction.events.CreateTransferEvent;
import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;

@Component
public class TransactionProducer {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publishCreateTransfer(CreateTransferEvent event) {
        rabbitTemplate.convertAndSend(
            RabbitNames.TRANSACTION_EXCHANGE,
            RabbitNames.TRANSFER_CREATE_ROUTING_KEY,
            event
        );
    }

    public void publishTransactionCompleted(TransactionCompletedEvent event) {
        rabbitTemplate.convertAndSend(
            RabbitNames.TRANSACTION_EXCHANGE,
            RabbitNames.TRANSACTION_COMPLETED_ROUTING_KEY,
            event
        );
    }
}