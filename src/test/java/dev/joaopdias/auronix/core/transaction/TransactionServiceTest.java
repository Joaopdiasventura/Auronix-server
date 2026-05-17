package dev.joaopdias.auronix.core.transaction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import dev.joaopdias.auronix.core.account.AccountRepository;
import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.transaction.dto.CreateTransferDto;
import dev.joaopdias.auronix.core.transaction.dto.TransactionResponseDto;
import dev.joaopdias.auronix.core.transaction.entities.LedgerTransaction;
import dev.joaopdias.auronix.core.transaction.enums.LedgerTransactionStatus;
import dev.joaopdias.auronix.core.transaction.enums.LedgerTransactionType;
import dev.joaopdias.auronix.core.transaction.events.CreateTransferEvent;
import dev.joaopdias.auronix.core.transaction.events.TransactionCompletedEvent;
import dev.joaopdias.auronix.core.user.entities.User;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    private static final UUID PAYER_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID PAYEE_USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd11");
    private static final UUID PAYER_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd12");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");
    private static final UUID TRANSACTION_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionProducer transactionProducer;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Account payerAccount;
    private Account payeeAccount;

    @BeforeEach
    void setUp() {
        payerAccount = account(PAYER_ACCOUNT_ID, PAYER_USER_ID, "payer@example.com", 1000L);
        payeeAccount = account(PAYEE_ACCOUNT_ID, PAYEE_USER_ID, "payee@example.com", 200L);
    }

    @Test
    void createPublishesTransferEventWhenValid() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(accountRepository.findById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.of(payeeAccount));

        transactionService.create(PAYER_USER_ID, new CreateTransferDto(PAYEE_ACCOUNT_ID, 300L));

        ArgumentCaptor<CreateTransferEvent> eventCaptor = ArgumentCaptor.forClass(CreateTransferEvent.class);
        verify(transactionProducer).publishCreateTransfer(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 300L));
    }

    @Test
    void createThrowsBadRequestWhenAmountIsInvalid() {
        assertStatus(
            () -> transactionService.create(PAYER_USER_ID, new CreateTransferDto(PAYEE_ACCOUNT_ID, 0L)),
            HttpStatus.BAD_REQUEST
        );

        verify(accountRepository, never()).findByUserId(any());
        verify(transactionProducer, never()).publishCreateTransfer(any());
    }

    @Test
    void createThrowsNotFoundWhenPayerAccountIsMissing() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.empty());

        assertStatus(
            () -> transactionService.create(PAYER_USER_ID, new CreateTransferDto(PAYEE_ACCOUNT_ID, 300L)),
            HttpStatus.NOT_FOUND
        );

        verify(transactionProducer, never()).publishCreateTransfer(any());
    }

    @Test
    void createThrowsBadRequestWhenPayeeIsPayerAccount() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));

        assertStatus(
            () -> transactionService.create(PAYER_USER_ID, new CreateTransferDto(PAYER_ACCOUNT_ID, 300L)),
            HttpStatus.BAD_REQUEST
        );

        verify(accountRepository, never()).findById(any());
        verify(transactionProducer, never()).publishCreateTransfer(any());
    }

    @Test
    void createThrowsBadRequestWhenBalanceIsInsufficient() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));

        assertStatus(
            () -> transactionService.create(PAYER_USER_ID, new CreateTransferDto(PAYEE_ACCOUNT_ID, 1001L)),
            HttpStatus.BAD_REQUEST
        );

        verify(accountRepository, never()).findById(any());
        verify(transactionProducer, never()).publishCreateTransfer(any());
    }

    @Test
    void createThrowsNotFoundWhenPayeeAccountIsMissing() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(accountRepository.findById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.empty());

        assertStatus(
            () -> transactionService.create(PAYER_USER_ID, new CreateTransferDto(PAYEE_ACCOUNT_ID, 300L)),
            HttpStatus.NOT_FOUND
        );

        verify(transactionProducer, never()).publishCreateTransfer(any());
    }

    @Test
    void transferMovesMoneyAndCreatesCompletedTransferLedgerEntry() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(accountRepository.findById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.of(payeeAccount));

        transactionService.transfer(new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 300L));

        ArgumentCaptor<LedgerTransaction> transactionCaptor = ArgumentCaptor.forClass(LedgerTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        verify(accountRepository).save(payerAccount);
        verify(accountRepository).save(payeeAccount);

        LedgerTransaction transaction = transactionCaptor.getValue();
        assertThat(payerAccount.getBalance()).isEqualTo(700L);
        assertThat(payeeAccount.getBalance()).isEqualTo(500L);
        assertThat(transaction.getPayerAccount()).isSameAs(payerAccount);
        assertThat(transaction.getPayeeAccount()).isSameAs(payeeAccount);
        assertThat(transaction.getAmount()).isEqualTo(300L);
        assertThat(transaction.getPayerBalanceBefore()).isEqualTo(1000L);
        assertThat(transaction.getPayerBalanceAfter()).isEqualTo(700L);
        assertThat(transaction.getPayeeBalanceBefore()).isEqualTo(200L);
        assertThat(transaction.getPayeeBalanceAfter()).isEqualTo(500L);
        assertThat(transaction.getType()).isEqualTo(LedgerTransactionType.TRANSFER);
        assertThat(transaction.getStatus()).isEqualTo(LedgerTransactionStatus.COMPLETED);
    }

    @Test
    void transferPublishesTransactionCompletedAfterCommit() {
        Instant createdAt = Instant.parse("2026-05-17T00:00:00Z");
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(accountRepository.findById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.of(payeeAccount));
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(invocation -> {
            LedgerTransaction transaction = invocation.getArgument(0);
            transaction.setId(TRANSACTION_ID);
            transaction.setCreatedAt(createdAt);
            return transaction;
        });

        TransactionSynchronizationManager.initSynchronization();

        try {
            transactionService.transfer(new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 300L));

            verify(transactionProducer, never()).publishTransactionCompleted(any());

            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

            ArgumentCaptor<TransactionCompletedEvent> eventCaptor = ArgumentCaptor.forClass(TransactionCompletedEvent.class);
            verify(transactionProducer).publishTransactionCompleted(eventCaptor.capture());

            assertThat(eventCaptor.getValue()).isEqualTo(new TransactionCompletedEvent(
                TRANSACTION_ID,
                300L,
                PAYER_ACCOUNT_ID,
                PAYEE_ACCOUNT_ID,
                createdAt,
                "transaction.completed"
            ));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void transferThrowsNotFoundWhenPayerAccountIsMissing() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.empty());

        assertStatus(
            () -> transactionService.transfer(new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 300L)),
            HttpStatus.NOT_FOUND
        );

        verifyNoInteractions(transactionProducer);
    }

    @Test
    void transferThrowsNotFoundWhenPayeeAccountIsMissing() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(accountRepository.findById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.empty());

        assertStatus(
            () -> transactionService.transfer(new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 300L)),
            HttpStatus.NOT_FOUND
        );
    }

    @Test
    void transferThrowsBadRequestWhenAccountsAreTheSame() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(accountRepository.findById(PAYER_ACCOUNT_ID)).thenReturn(Optional.of(payerAccount));

        assertStatus(
            () -> transactionService.transfer(new CreateTransferEvent(PAYER_USER_ID, PAYER_ACCOUNT_ID, 300L)),
            HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void transferThrowsBadRequestWhenAmountIsInvalid() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(accountRepository.findById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.of(payeeAccount));

        assertStatus(
            () -> transactionService.transfer(new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 0L)),
            HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void transferThrowsBadRequestWhenBalanceIsInsufficient() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(accountRepository.findById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.of(payeeAccount));

        assertStatus(
            () -> transactionService.transfer(new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 1001L)),
            HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void transferThrowsConflictWhenOptimisticLockFails() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(accountRepository.findById(PAYEE_ACCOUNT_ID)).thenReturn(Optional.of(payeeAccount));
        when(transactionRepository.save(any(LedgerTransaction.class)))
            .thenThrow(new ObjectOptimisticLockingFailureException(Account.class, PAYER_ACCOUNT_ID));

        assertStatus(
            () -> transactionService.transfer(new CreateTransferEvent(PAYER_USER_ID, PAYEE_ACCOUNT_ID, 300L)),
            HttpStatus.CONFLICT
        );
    }

    @Test
    void findByUserIdReturnsMappedTransactions() {
        LedgerTransaction transaction = ledgerTransaction();
        PageRequest pageable = PageRequest.of(0, 10);
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.of(payerAccount));
        when(transactionRepository.findByPayerAccountIdOrPayeeAccountId(PAYER_ACCOUNT_ID, PAYER_ACCOUNT_ID, pageable))
            .thenReturn(new PageImpl<>(List.of(transaction), pageable, 1));

        Page<TransactionResponseDto> page = transactionService.findByUserId(PAYER_USER_ID, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        TransactionResponseDto dto = page.getContent().getFirst();
        assertThat(dto.id()).isEqualTo(TRANSACTION_ID);
        assertThat(dto.payer().email()).isEqualTo("payer@example.com");
        assertThat(dto.payee().email()).isEqualTo("payee@example.com");
        assertThat(dto.amount()).isEqualTo(300L);
    }

    @Test
    void findByUserIdThrowsNotFoundWhenAccountIsMissing() {
        when(accountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.empty());

        assertStatus(() -> transactionService.findByUserId(PAYER_USER_ID, PageRequest.of(0, 10)), HttpStatus.NOT_FOUND);
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(status);
    }

    private static Account account(UUID accountId, UUID userId, String email, long balance) {
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setName(email);
        user.setPassword("hashed-password");

        Account account = new Account();
        account.setId(accountId);
        account.setUser(user);
        account.setBalance(balance);
        return account;
    }

    private LedgerTransaction ledgerTransaction() {
        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setPayerAccount(payerAccount);
        transaction.setPayeeAccount(payeeAccount);
        transaction.setAmount(300L);
        transaction.setPayerBalanceBefore(1000L);
        transaction.setPayerBalanceAfter(700L);
        transaction.setPayeeBalanceBefore(200L);
        transaction.setPayeeBalanceAfter(500L);
        transaction.setType(LedgerTransactionType.TRANSFER);
        transaction.setStatus(LedgerTransactionStatus.COMPLETED);
        transaction.setCreatedAt(Instant.parse("2026-05-17T00:00:00Z"));
        return transaction;
    }
}
