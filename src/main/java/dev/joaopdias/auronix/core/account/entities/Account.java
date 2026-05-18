package dev.joaopdias.auronix.core.account.entities;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import dev.joaopdias.auronix.core.account.dto.AccountResponseDto;
import dev.joaopdias.auronix.core.user.entities.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_id", nullable = false, unique=true)
    private User user;

    @Column(nullable = false)
    private long balance;

    @Version
    private long version;

    public AccountResponseDto toResponseDto() {
        return new AccountResponseDto(
            this.id,
            this.user.getEmail(),
            this.user.getName(),
            this.user.getCreatedAt(),
            this.balance
        );
    }
}
