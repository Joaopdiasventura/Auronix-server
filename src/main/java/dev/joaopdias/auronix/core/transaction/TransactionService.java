package dev.joaopdias.auronix.core.transaction;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
public class TransactionService {
    
    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionProducer transactionProducer;

    @Autowired
    private AccountRepository accountRepository;

    @Transactional
    public void create(UUID payerUserId, CreateTransferDto createTransferDto) {
        if (createTransferDto.amount() <= 0) 
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido");

        Account payerAccount = accountRepository.findByUserId(payerUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta de origem não encontrada"));

        if (payerAccount.getId().equals(createTransferDto.payeeAccountId())) 
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível transferir para a própria conta");
        
        if (payerAccount.getBalance() < createTransferDto.amount()) 
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente");

        accountRepository.findById(createTransferDto.payeeAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta de destino não encontrada"));

        CreateTransferEvent event = new CreateTransferEvent(
            UUID.randomUUID(),
            payerUserId,
            createTransferDto.payeeAccountId(),
            createTransferDto.amount()
        );

        transactionProducer.publishCreateTransfer(event);
    }

    @Transactional
    public void transfer(CreateTransferEvent event) {
        try {
            if (event.amount() <= 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido");

            UUID payerAccountId = accountRepository.findIdByUserId(event.payerUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta de origem não encontrada"));

            if (payerAccountId.equals(event.payeeAccountId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível transferir para a própria conta");

            List<UUID> accountIds = List.of(payerAccountId, event.payeeAccountId())
                .stream()
                .sorted(Comparator.naturalOrder())
                .toList();

            Map<UUID, Account> lockedAccounts = accountRepository.findAllByIdInForUpdate(accountIds)
                .stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

            Account payerAccount = lockedAccounts.get(payerAccountId);
            Account payeeAccount = lockedAccounts.get(event.payeeAccountId());

            if (payerAccount == null)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta de origem não encontrada");

            if (payeeAccount == null)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta de destino não encontrada");
            
            if (payerAccount.getBalance() < event.amount()) 
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente");

            long payerBalanceBefore = payerAccount.getBalance();
            long payeeBalanceBefore = payeeAccount.getBalance();

            long payerBalanceAfter = payerBalanceBefore - event.amount();
            long payeeBalanceAfter = payeeBalanceBefore + event.amount();

            payerAccount.setBalance(payerBalanceAfter);
            payeeAccount.setBalance(payeeBalanceAfter);

            LedgerTransaction transaction = new LedgerTransaction();

            transaction.setPayerAccount(payerAccount);
            transaction.setPayeeAccount(payeeAccount);
            transaction.setAmount(event.amount());
            transaction.setPayerBalanceBefore(payerBalanceBefore);
            transaction.setPayerBalanceAfter(payerBalanceAfter);
            transaction.setPayeeBalanceBefore(payeeBalanceBefore);
            transaction.setPayeeBalanceAfter(payeeBalanceAfter);
            transaction.setType(LedgerTransactionType.TRANSFER);
            transaction.setStatus(LedgerTransactionStatus.COMPLETED);

            transactionRepository.save(transaction);

            accountRepository.save(payerAccount);
            accountRepository.save(payeeAccount);

            TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                UUID.randomUUID(),
                transaction.getId(),
                transaction.getAmount(),
                payerAccount.getId(),
                payeeAccount.getId(),
                transaction.getCreatedAt(),
                "transaction.completed"
            );

            transactionProducer.publishTransactionCompleted(completedEvent);
        } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A conta foi atualizada por outra transação. Tente novamente"
            );
        }
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponseDto> findByUserId(UUID userId, Pageable pageable) {
        Account account = accountRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada"));

        return transactionRepository
            .findByPayerAccountIdOrPayeeAccountId(account.getId(), account.getId(), pageable)
            .map((t) -> t.toResponseDto());
    }

    @Transactional(readOnly = true)
    public TransactionResponseDto findById(UUID userId, UUID transactionId) {
        Account account = accountRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada"));

        LedgerTransaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transação não encontrada"));

        boolean isPayer = transaction.getPayerAccount().getId().equals(account.getId());
        boolean isPayee = transaction.getPayeeAccount().getId().equals(account.getId());

        if (!isPayer && !isPayee) 
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transação não encontrada");

        return transaction.toResponseDto();
    }

}
