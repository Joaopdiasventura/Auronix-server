package dev.joaopdias.auronix.core.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.user.entities.User;
import jakarta.persistence.LockModeType;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID>{
    Optional<Account> findByUser(User user);

    Optional<Account> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
    
    @Query("select account.id from Account account where account.user.email = :userEmail")
    Optional<UUID> findIdByUserEmail(@Param("userEmail") String userEmail);

    @Query("select account.id from Account account where account.user.id = :userId")
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId);

    @Query("select account.user.id from Account account where account.id = :accountId")
    Optional<UUID> findUserIdById(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account join fetch account.user where account.id in :accountIds order by account.id")
    List<Account> findAllByIdInForUpdate(@Param("accountIds") Collection<UUID> accountIds);
}
