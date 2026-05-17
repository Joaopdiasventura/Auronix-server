package dev.joaopdias.auronix.core.transaction;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import dev.joaopdias.auronix.core.transaction.entities.LedgerTransaction;

public interface TransactionRepository extends JpaRepository<LedgerTransaction, UUID>{
    Page<LedgerTransaction> findByPayerAccountIdOrPayeeAccountId(
        UUID payerAccountId,
        UUID payeeAccountId,
        Pageable pageable
    );
}
