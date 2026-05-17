package dev.joaopdias.auronix.core.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.user.entities.User;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID>{
    Optional<Account> findByUser(User user);

    Optional<Account> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    @Query("select account.user.id from Account account where account.id = :accountId")
    Optional<UUID> findUserIdById(@Param("accountId") UUID accountId);
}
