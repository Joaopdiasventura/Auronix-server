package dev.joaopdias.auronix.core.user.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.joaopdias.auronix.core.user.dto.UserResponseDto;

class UserTest {
    @Test
    void prePersistSetsCreatedAt() {
        User user = new User();

        user.prePersist();

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void toResponseDtoPreservesPublicFields() {
        UUID id = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
        Instant createdAt = Instant.parse("2026-05-16T12:00:00Z");
        User user = new User();
        user.setId(id);
        user.setEmail("joao@example.com");
        user.setName("Joao");
        user.setPassword("secret-hash");
        user.setCreatedAt(createdAt);

        UserResponseDto response = user.toResponseDto();

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.email()).isEqualTo("joao@example.com");
        assertThat(response.name()).isEqualTo("Joao");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
