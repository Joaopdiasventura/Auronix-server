package dev.joaopdias.auronix.core.paymentrequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.joaopdias.auronix.core.paymentrequest.entities.PaymentRequest;

@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {
    @Query("select paymentRequest from PaymentRequest paymentRequest join fetch paymentRequest.user where paymentRequest.id = :id and paymentRequest.expiresAt > :now")
    Optional<PaymentRequest> findActiveById(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("delete from PaymentRequest paymentRequest where paymentRequest.id = :id and paymentRequest.expiresAt <= :now")
    int deleteExpiredById(@Param("id") UUID id, @Param("now") Instant now);
}
