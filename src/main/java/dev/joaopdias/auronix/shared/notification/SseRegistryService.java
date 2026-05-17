package dev.joaopdias.auronix.shared.notification;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.joaopdias.auronix.shared.notification.dto.SseConnectionMetadata;
import dev.joaopdias.auronix.shared.notification.dto.TransactionNotificationDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class SseRegistryService {
    private static final Duration REGISTRY_TTL = Duration.ofMinutes(30);
    private static final long EMITTER_TIMEOUT = REGISTRY_TTL.toMillis();
    private static final String KEY_PREFIX = "notifications:sse";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.instance-id}")
    private String instanceId;

    private final Map<UUID, Map<UUID, SseEmitter>> localEmittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        UUID connectionId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);

        localEmittersByUserId
            .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
            .put(connectionId, emitter);

        try {
            registerMetadata(userId, connectionId);
        } catch (RuntimeException exception) {
            removeLocalEmitter(userId, connectionId);
            throw exception;
        }

        emitter.onCompletion(() -> unregister(userId, connectionId));
        emitter.onTimeout(() -> unregister(userId, connectionId));
        emitter.onError(ignored -> unregister(userId, connectionId));

        return emitter;
    }

    public boolean send(UUID userId, TransactionNotificationDto notification) {
        Map<UUID, SseEmitter> userEmitters = localEmittersByUserId.get(userId);

        if (userEmitters == null || userEmitters.isEmpty()) return false;

        boolean sent = false;

        for (Map.Entry<UUID, SseEmitter> entry : userEmitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name(notification.type()).data(notification));
                sent = true;
            } catch (IOException | IllegalStateException exception) {
                unregister(userId, entry.getKey());
            }
        }

        return sent;
    }

    private void unregister(UUID userId, UUID connectionId) {
        removeLocalEmitter(userId, connectionId);
        removeMetadata(userId, connectionId);
    }

    private void removeLocalEmitter(UUID userId, UUID connectionId) {
        Map<UUID, SseEmitter> userEmitters = localEmittersByUserId.get(userId);

        if (userEmitters != null) {
            userEmitters.remove(connectionId);

            if (userEmitters.isEmpty()) 
                localEmittersByUserId.remove(userId);
        }
    }

    private void registerMetadata(UUID userId, UUID connectionId) {
        Instant connectedAt = Instant.now();
        Instant expiresAt = connectedAt.plus(REGISTRY_TTL);
        SseConnectionMetadata metadata = new SseConnectionMetadata(
            userId,
            connectionId,
            connectedAt,
            expiresAt,
            instanceId
        );

        redisTemplate.opsForSet().add(userConnectionsKey(userId), connectionId.toString());
        redisTemplate.expire(userConnectionsKey(userId), REGISTRY_TTL);
        redisTemplate.opsForValue().set(connectionMetadataKey(userId, connectionId), toJson(metadata), REGISTRY_TTL);
    }

    private void removeMetadata(UUID userId, UUID connectionId) {
        redisTemplate.opsForSet().remove(userConnectionsKey(userId), connectionId.toString());
        redisTemplate.delete(connectionMetadataKey(userId, connectionId));

        Long size = redisTemplate.opsForSet().size(userConnectionsKey(userId));

        if (size == null || size == 0) 
            redisTemplate.delete(userConnectionsKey(userId));
    }

    private String toJson(SseConnectionMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível registrar a conexão SSE.", exception);
        }
    }

    private String userConnectionsKey(UUID userId) {
        return KEY_PREFIX + ":user:" + userId;
    }

    private String connectionMetadataKey(UUID userId, UUID connectionId) {
        return KEY_PREFIX + ":connection:" + userId + ":" + connectionId;
    }
}
