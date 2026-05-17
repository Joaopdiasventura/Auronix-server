package dev.joaopdias.auronix.core.account;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.user.UserRepository;
import dev.joaopdias.auronix.core.user.entities.User;

@Service
public class AccountService {
    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;


    @Transactional
    public Account create(UUID userId) {
        if (accountRepository.existsByUserId(userId)) 
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuário já possui conta");

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        Account account = new Account();

        account.setUser(user);
        account.setBalance(1000_00L);

        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account findByUserId(UUID user) {
        return accountRepository.findByUserId(user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada"));
    }

    @Transactional(readOnly = true)
    public Account findById(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada"));
    }

    @Transactional(readOnly = true)
    public long getBalance(UUID userId) {
        return findByUserId(userId).getBalance();
    }
}
