package dev.joaopdias.auronix.shared.notification;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.joaopdias.auronix.core.account.AccountRepository;
import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;
import dev.joaopdias.auronix.shared.notification.dto.TransactionNotificationDto;

@Service
public class NotificationService {
    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SseRegistryService sseRegistryService;

    public void notifyTransactionCompleted(TransactionCompletedEvent event) {
        TransactionNotificationDto notification = new TransactionNotificationDto(
            event.transactionId(),
            event.amount(),
            event.payerAccountId(),
            event.payeeAccountId(),
            event.createdAt(),
            event.type()
        );

        Set<UUID> userIds = new LinkedHashSet<>();

        accountRepository.findUserIdById(event.payerAccountId()).ifPresent(userIds::add);
        accountRepository.findUserIdById(event.payeeAccountId()).ifPresent(userIds::add);

        userIds.forEach(userId -> sseRegistryService.send(userId, notification));
    }
}
