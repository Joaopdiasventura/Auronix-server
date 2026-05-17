package dev.joaopdias.auronix.core.paymentrequest.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.paymentrequest.dto.PaymentRequestResponseDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "payment_requests",
    indexes = {
        @Index(name = "idx_payment_requests_user_id", columnList = "fk_account_id"),
        @Index(name = "idx_payment_requests_expires_at", columnList = "expires_at")
    }
)
public class PaymentRequest {
    private static final long EXPIRES_IN_SECONDS = 10 * 60;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private long value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_account_id", nullable = false)
    private Account account;

    @Column(nullable = false, name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();

        if (this.createdAt == null)
            this.createdAt = now;

        if (this.expiresAt == null)
            this.expiresAt = now.plusSeconds(EXPIRES_IN_SECONDS);
    }

    public PaymentRequestResponseDto toResponseDto() {
        return new PaymentRequestResponseDto(
            this.id,
            this.value,
            this.account.toResponseDto(),
            this.createdAt
        );
    }
}
