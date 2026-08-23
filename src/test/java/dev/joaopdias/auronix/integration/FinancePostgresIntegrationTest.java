package dev.joaopdias.auronix.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.joaopdias.auronix.core.account.AccountRepository;
import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.transaction.events.CreateTransferEvent;
import dev.joaopdias.auronix.core.user.UserRepository;
import dev.joaopdias.auronix.core.user.entities.User;
import dev.joaopdias.auronix.shared.messaging.ProcessedEventRepository;
import dev.joaopdias.auronix.shared.outbox.OutboxEventRepository;
import dev.joaopdias.auronix.shared.outbox.OutboxService;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FinancePostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("auronix")
        .withUsername("auronix")
        .withPassword("auronix");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void databaseRejectsNegativeAccountBalance() {
        User user = user("negative-balance@example.com");
        Account account = new Account();
        account.setUser(user);
        account.setBalance(-1);

        assertThatThrownBy(() -> accountRepository.saveAndFlush(account))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateEventInsertIsAtomicUnderConcurrency() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Callable<Integer> insert = () -> {
            ready.countDown();
            start.await();
            return transactionTemplate.execute(status -> processedEventRepository.insertIfAbsent(
                UUID.randomUUID(),
                eventId,
                "transfer.create",
                aggregateId
            ));
        };

        try {
            List<Future<Integer>> futures = List.of(executorService.submit(insert), executorService.submit(insert));
            ready.await();
            start.countDown();

            int inserted = futures.getFirst().get() + futures.getLast().get();

            assertThat(inserted).isEqualTo(1);
            assertThat(processedEventRepository.count()).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void outboxEntryRollsBackWithBusinessTransaction() {
        User user = user("outbox-rollback@example.com");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Account account = new Account();
            account.setUser(user);
            account.setBalance(500);
            accountRepository.save(account);
            outboxService.enqueue(
                UUID.randomUUID(),
                "transfer.create",
                account.getId(),
                "transactions.exchange",
                "transaction.transfer.create",
                new CreateTransferEvent(user.getId(), account.getId(), 100)
            );
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxEventRepository.count()).isZero();
        assertThat(accountRepository.count()).isZero();
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setPassword("hashed-password");
        return userRepository.saveAndFlush(user);
    }
}
