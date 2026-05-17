package dev.joaopdias.auronix.core.transaction.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.transaction.dto.TransactionResponseDto;
import dev.joaopdias.auronix.core.transaction.enums.LedgerTransactionStatus;
import dev.joaopdias.auronix.core.transaction.enums.LedgerTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "ledger_transactions",
    indexes = {
        @Index(name = "idx_ledger_payer_account_created_at", columnList = "fk_payer_account_id, created_at"),
        @Index(name = "idx_ledger_payee_account_created_at", columnList = "fk_payee_account_id, created_at")
    }
)
public class LedgerTransaction {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_payer_account_id", nullable = false)
    private Account payerAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_payee_account_id", nullable = false)
    private Account payeeAccount;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, name = "payer_balance_before")
    private long payerBalanceBefore;

    @Column(nullable = false, name = "payer_balance_after")
    private long payerBalanceAfter;

    @Column(nullable = false, name = "payee_balance_before")
    private long payeeBalanceBefore;

    @Column(nullable = false, name = "payee_balance_after")
    private long payeeBalanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LedgerTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LedgerTransactionStatus status;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public TransactionResponseDto toResponseDto() {
        return new TransactionResponseDto(
            this.id,
            this.payerAccount.getUser().getEmail(),
            this.payeeAccount.getUser().getEmail(),
            this.amount,
            this.payerBalanceBefore,
            this.payerBalanceAfter,
            this.payeeBalanceBefore,
            this.payeeBalanceAfter,
            this.createdAt
        );
    }
}