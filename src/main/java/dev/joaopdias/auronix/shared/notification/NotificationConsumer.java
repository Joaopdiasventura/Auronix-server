package dev.joaopdias.auronix.shared.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.joaopdias.auronix.config.RabbitNames;
import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;

@Component
public class NotificationConsumer {
    @Autowired
    private NotificationService notificationService;

    @RabbitListener(queues = RabbitNames.TRANSACTION_COMPLETED_QUEUE)
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        notificationService.notifyTransactionCompleted(event);
    }
}
