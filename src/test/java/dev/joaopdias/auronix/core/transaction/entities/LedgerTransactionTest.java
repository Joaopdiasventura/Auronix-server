package dev.joaopdias.auronix.core.transaction.entities;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.transaction.dto.TransactionResponseDto;
import dev.joaopdias.auronix.core.transaction.enums.LedgerTransactionStatus;
import dev.joaopdias.auronix.core.transaction.enums.LedgerTransactionType;
import dev.joaopdias.auronix.core.user.entities.User;

class LedgerTransactionTest {
    private static final UUID TRANSACTION_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");

    @Test
    void prePersistSetsCreatedAt() {
        LedgerTransaction transaction = new LedgerTransaction();

        transaction.prePersist();

        assertThat(transaction.getCreatedAt()).isNotNull();
        assertThat(transaction.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void toResponseDtoPreservesTransferFields() {
        Instant createdAt = Instant.parse("2026-05-17T00:00:00Z");
        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setPayerAccount(account("payer@example.com"));
        transaction.setPayeeAccount(account("payee@example.com"));
        transaction.setAmount(300L);
        transaction.setPayerBalanceBefore(1000L);
        transaction.setPayerBalanceAfter(700L);
        transaction.setPayeeBalanceBefore(200L);
        transaction.setPayeeBalanceAfter(500L);
        transaction.setType(LedgerTransactionType.TRANSFER);
        transaction.setStatus(LedgerTransactionStatus.COMPLETED);
        transaction.setCreatedAt(createdAt);

        TransactionResponseDto response = transaction.toResponseDto();

        assertThat(response.id()).isEqualTo(TRANSACTION_ID);
        assertThat(response.payer().email()).isEqualTo("payer@example.com");
        assertThat(response.payee().email()).isEqualTo("payee@example.com");
        assertThat(response.amount()).isEqualTo(300L);
        assertThat(response.payerBalanceBefore()).isEqualTo(1000L);
        assertThat(response.payerBalanceAfter()).isEqualTo(700L);
        assertThat(response.payeeBalanceBefore()).isEqualTo(200L);
        assertThat(response.payeeBalanceAfter()).isEqualTo(500L);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    private static Account account(String email) {
        User user = new User();
        user.setEmail(email);

        Account account = new Account();
        account.setUser(user);
        return account;
    }
}
