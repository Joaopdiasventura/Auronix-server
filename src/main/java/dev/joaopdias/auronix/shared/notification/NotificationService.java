package dev.joaopdias.auronix.shared.notification;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import dev.joaopdias.auronix.core.account.AccountRepository;
import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;
import dev.joaopdias.auronix.shared.notification.dto.RealtimeNotificationDto;
import dev.joaopdias.auronix.shared.notification.dto.TransactionNotificationDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationService {
    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChannelTopic realtimeNotificationsTopic;

    @Autowired
    private ObjectMapper objectMapper;

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

        userIds.forEach(userId -> publishRealtimeNotification(userId, notification));
    }

    private void publishRealtimeNotification(UUID userId, TransactionNotificationDto notification) {
        try {
            RealtimeNotificationDto realtimeNotification = new RealtimeNotificationDto(userId, notification);
            redisTemplate.convertAndSend(
                realtimeNotificationsTopic.getTopic(),
                objectMapper.writeValueAsString(realtimeNotification)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Nao foi possivel publicar notificacao realtime", exception);
        }
    }
}
