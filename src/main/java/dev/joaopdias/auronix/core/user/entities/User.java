package dev.joaopdias.auronix.core.user.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import dev.joaopdias.auronix.core.user.dto.UserResponseDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "users")
public class User {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable=false, unique=true)
    private String email;

    @Column(nullable=false, length=150)
    private String name;

    @Column(nullable=false)
    private String password;

    @Column(nullable=false, name="created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public UserResponseDto toResponseDto() {
        return new UserResponseDto(
            this.id,
            this.email,
            this.name,
            this.createdAt
        );
    }
}