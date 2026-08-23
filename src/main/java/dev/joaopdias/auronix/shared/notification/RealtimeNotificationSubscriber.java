package dev.joaopdias.auronix.shared.notification;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import dev.joaopdias.auronix.shared.notification.dto.RealtimeNotificationDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class RealtimeNotificationSubscriber implements MessageListener {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SseRegistryService sseRegistryService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RealtimeNotificationDto realtimeNotification = objectMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8),
                RealtimeNotificationDto.class
            );
            sseRegistryService.send(realtimeNotification.userId(), realtimeNotification.notification());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Nao foi possivel processar notificacao realtime", exception);
        }
    }
}
