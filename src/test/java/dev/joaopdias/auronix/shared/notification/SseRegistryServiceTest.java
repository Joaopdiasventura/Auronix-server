package dev.joaopdias.auronix.shared.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.joaopdias.auronix.shared.notification.dto.SseConnectionMetadata;
import dev.joaopdias.auronix.shared.notification.dto.TransactionNotificationDto;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SseRegistryServiceTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID PAYER_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd12");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");
    private static final UUID TRANSACTION_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");
    private static final String USER_KEY = "notifications:sse:user:" + USER_ID;
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String INSTANCE_ID = "test-instance";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SseRegistryService sseRegistryService;

    @BeforeEach
    void setUp() {
        sseRegistryService = new SseRegistryService();
        ReflectionTestUtils.setField(sseRegistryService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(sseRegistryService, "objectMapper", new JsonMapper());
        ReflectionTestUtils.setField(sseRegistryService, "instanceId", INSTANCE_ID);
    }

    @Test
    void registerStoresEmitterLocallyAndRedisMetadata() throws Exception {
        ArgumentCaptor<String> connectionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SseEmitter emitter = sseRegistryService.register(USER_ID);

        assertThat(emitter).isNotNull();
        verify(setOperations).add(eq(USER_KEY), connectionCaptor.capture());
        verify(redisTemplate).expire(USER_KEY, TTL);
        verify(valueOperations).set(
            startsWith("notifications:sse:connection:" + USER_ID + ":"),
            metadataCaptor.capture(),
            eq(TTL)
        );

        SseConnectionMetadata metadata = new JsonMapper().readValue(metadataCaptor.getValue(), SseConnectionMetadata.class);
        assertThat(metadata.userId()).isEqualTo(USER_ID);
        assertThat(metadata.connectionId()).isEqualTo(UUID.fromString(connectionCaptor.getValue()));
        assertThat(metadata.connectedAt()).isNotNull();
        assertThat(metadata.expiresAt()).isEqualTo(metadata.connectedAt().plus(TTL));
        assertThat(metadata.instanceId()).isEqualTo(INSTANCE_ID);

        Map<UUID, Map<UUID, SseEmitter>> localEmittersByUserId = localEmittersByUserId();
        assertThat(localEmittersByUserId.get(USER_ID)).containsEntry(metadata.connectionId(), emitter);
    }

    @Test
    void sendReturnsFalseWhenThereIsNoLocalEmitter() {
        boolean sent = sseRegistryService.send(USER_ID, notification());

        assertThat(sent).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void sendReturnsTrueForRegisteredLocalUser() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        sseRegistryService.register(USER_ID);

        boolean sent = sseRegistryService.send(USER_ID, notification());

        assertThat(sent).isTrue();
    }

    @Test
    void unregisterRemovesLocalEmitterAndRedisMetadata() {
        ArgumentCaptor<String> connectionCaptor = ArgumentCaptor.forClass(String.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size(USER_KEY)).thenReturn(0L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        sseRegistryService.register(USER_ID);
        verify(setOperations).add(eq(USER_KEY), connectionCaptor.capture());

        ReflectionTestUtils.invokeMethod(
            sseRegistryService,
            "unregister",
            USER_ID,
            UUID.fromString(connectionCaptor.getValue())
        );

        assertThat(localEmittersByUserId()).doesNotContainKey(USER_ID);
        verify(setOperations).remove(eq(USER_KEY), anyString());
        verify(redisTemplate).delete(startsWith("notifications:sse:connection:" + USER_ID + ":"));
        verify(redisTemplate).delete(USER_KEY);
    }

    @Test
    void sendRemovesBrokenLocalEmitterAndRedisMetadata() throws Exception {
        UUID connectionId = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd16");
        SseEmitter emitter = org.mockito.Mockito.mock(SseEmitter.class);
        Map<UUID, SseEmitter> userEmitters = new ConcurrentHashMap<>();
        userEmitters.put(connectionId, emitter);
        localEmittersByUserId().put(USER_ID, userEmitters);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size(USER_KEY)).thenReturn(0L);
        doThrow(new IOException()).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        boolean sent = sseRegistryService.send(USER_ID, notification());

        assertThat(sent).isFalse();
        assertThat(localEmittersByUserId()).doesNotContainKey(USER_ID);
        verify(setOperations).remove(USER_KEY, connectionId.toString());
        verify(redisTemplate).delete("notifications:sse:connection:" + USER_ID + ":" + connectionId);
        verify(redisTemplate).delete(USER_KEY);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Map<UUID, SseEmitter>> localEmittersByUserId() {
        return (Map<UUID, Map<UUID, SseEmitter>>) ReflectionTestUtils.getField(
            sseRegistryService,
            "localEmittersByUserId"
        );
    }

    private static TransactionNotificationDto notification() {
        return new TransactionNotificationDto(
            TRANSACTION_ID,
            300L,
            PAYER_ACCOUNT_ID,
            PAYEE_ACCOUNT_ID,
            Instant.parse("2026-05-17T00:00:00Z"),
            "transaction.completed"
        );
    }
}
