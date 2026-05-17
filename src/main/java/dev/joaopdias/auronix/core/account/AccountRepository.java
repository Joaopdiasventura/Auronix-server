package dev.joaopdias.auronix.core.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.user.entities.User;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID>{
    Optional<Account> findByUser(User user);

    Optional<Account> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
