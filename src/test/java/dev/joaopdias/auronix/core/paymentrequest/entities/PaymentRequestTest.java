package dev.joaopdias.auronix.core.paymentrequest.entities;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.paymentrequest.dto.PaymentRequestResponseDto;
import dev.joaopdias.auronix.core.user.entities.User;

class PaymentRequestTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd11");
    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");

    @Test
    void prePersistSetsCreatedAtAndExpiresAt() {
        PaymentRequest paymentRequest = paymentRequest();

        paymentRequest.prePersist();

        assertThat(paymentRequest.getCreatedAt()).isNotNull();
        assertThat(paymentRequest.getExpiresAt()).isEqualTo(paymentRequest.getCreatedAt().plusSeconds(600));
    }

    @Test
    void toResponseDtoReturnsSafeData() {
        Instant createdAt = Instant.parse("2026-05-17T00:00:00Z");
        PaymentRequest paymentRequest = paymentRequest();
        paymentRequest.setCreatedAt(createdAt);

        PaymentRequestResponseDto dto = paymentRequest.toResponseDto();

        assertThat(dto.id()).isEqualTo(PAYMENT_REQUEST_ID);
        assertThat(dto.value()).isEqualTo(300L);
        assertThat(dto.account().id()).isEqualTo(ACCOUNT_ID);
        assertThat(dto.createdAt()).isEqualTo(createdAt);
    }

    private static PaymentRequest paymentRequest() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("joao@example.com");
        user.setName("Joao Dias");

        Account account = new Account();
        account.setId(ACCOUNT_ID);
        account.setUser(user);

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setId(PAYMENT_REQUEST_ID);
        paymentRequest.setAccount(account);
        paymentRequest.setValue(300L);
        return paymentRequest;
    }
}
