package dev.joaopdias.auronix.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IdempotentMessageServiceTest {
    private static final UUID EVENT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd16");
    private static final UUID AGGREGATE_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd17");

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private IdempotentMessageService idempotentMessageService;

    @BeforeEach
    void setUp() {
        idempotentMessageService = new IdempotentMessageService();
        ReflectionTestUtils.setField(idempotentMessageService, "processedEventRepository", processedEventRepository);
    }

    @Test
    void processRunsActionWhenEventIsInserted() {
        AtomicInteger executions = new AtomicInteger();
        when(processedEventRepository.insertIfAbsent(
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.eq(EVENT_ID),
            org.mockito.ArgumentMatchers.eq("transfer.create"),
            org.mockito.ArgumentMatchers.eq(AGGREGATE_ID)
        )).thenReturn(1);

        boolean processed = idempotentMessageService.process(
            EVENT_ID,
            "transfer.create",
            AGGREGATE_ID,
            executions::incrementAndGet
        );

        assertThat(processed).isTrue();
        assertThat(executions).hasValue(1);
    }

    @Test
    void processSkipsActionWhenEventAlreadyExists() {
        AtomicInteger executions = new AtomicInteger();
        when(processedEventRepository.insertIfAbsent(
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.eq(EVENT_ID),
            org.mockito.ArgumentMatchers.eq("transfer.create"),
            org.mockito.ArgumentMatchers.eq(AGGREGATE_ID)
        )).thenReturn(0);

        boolean processed = idempotentMessageService.process(
            EVENT_ID,
            "transfer.create",
            AGGREGATE_ID,
            executions::incrementAndGet
        );

        assertThat(processed).isFalse();
        assertThat(executions).hasValue(0);
    }

    @Test
    void processPropagatesBusinessFailureForTransactionRollback() {
        RuntimeException failure = new RuntimeException("failed");
        when(processedEventRepository.insertIfAbsent(
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.eq(EVENT_ID),
            org.mockito.ArgumentMatchers.eq("transfer.create"),
            org.mockito.ArgumentMatchers.eq(AGGREGATE_ID)
        )).thenReturn(1);

        assertThatThrownBy(() -> idempotentMessageService.process(
            EVENT_ID,
            "transfer.create",
            AGGREGATE_ID,
            () -> {
                throw failure;
            }
        )).isSameAs(failure);

        verify(processedEventRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
