package dev.joaopdias.auronix.core.paymentrequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import dev.joaopdias.auronix.core.account.AccountService;
import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.paymentrequest.dto.CreatePaymentRequestDto;
import dev.joaopdias.auronix.core.paymentrequest.dto.PaymentRequestResponseDto;
import dev.joaopdias.auronix.core.paymentrequest.entities.PaymentRequest;
import dev.joaopdias.auronix.core.paymentrequest.events.PaymentRequestExpirationEvent;
import dev.joaopdias.auronix.core.user.entities.User;

@ExtendWith(MockitoExtension.class)
class PaymentRequestServiceTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd11");
    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");
    private static final Instant CREATED_AT = Instant.parse("2026-05-17T00:00:00Z");

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private PaymentRequestProducer paymentRequestProducer;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private PaymentRequestService paymentRequestService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = account();
    }

    @Test
    void createSavesPaymentRequestAndPublishesExpiration() {
        when(accountService.findByUserId(USER_ID)).thenReturn(account);
        when(paymentRequestRepository.save(any(PaymentRequest.class))).thenAnswer(invocation -> {
            PaymentRequest paymentRequest = invocation.getArgument(0);
            paymentRequest.setId(PAYMENT_REQUEST_ID);
            paymentRequest.setCreatedAt(CREATED_AT);
            return paymentRequest;
        });

        PaymentRequestResponseDto response = paymentRequestService.create(USER_ID, new CreatePaymentRequestDto(300L));

        ArgumentCaptor<PaymentRequest> paymentRequestCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        ArgumentCaptor<PaymentRequestExpirationEvent> eventCaptor = ArgumentCaptor.forClass(PaymentRequestExpirationEvent.class);
        verify(paymentRequestRepository).save(paymentRequestCaptor.capture());
        verify(paymentRequestProducer).publishExpiration(eventCaptor.capture());
        assertThat(paymentRequestCaptor.getValue().getAccount()).isSameAs(account);
        assertThat(paymentRequestCaptor.getValue().getValue()).isEqualTo(300L);
        assertThat(eventCaptor.getValue()).isEqualTo(new PaymentRequestExpirationEvent(PAYMENT_REQUEST_ID));
        assertThat(response).isEqualTo(new PaymentRequestResponseDto(PAYMENT_REQUEST_ID, 300L, account.toResponseDto(), CREATED_AT));
    }

    @Test
    void createRollsBackSavedPaymentRequestWhenExpirationPublishFails() {
        PaymentRequest saved = paymentRequest();
        when(accountService.findByUserId(USER_ID)).thenReturn(account);
        when(paymentRequestRepository.save(any(PaymentRequest.class))).thenReturn(saved);
        org.mockito.Mockito.doThrow(new RuntimeException("rabbit unavailable"))
            .when(paymentRequestProducer)
            .publishExpiration(any(PaymentRequestExpirationEvent.class));

        assertStatus(() -> paymentRequestService.create(USER_ID, new CreatePaymentRequestDto(300L)), HttpStatus.INTERNAL_SERVER_ERROR);

        verify(paymentRequestRepository).delete(saved);
    }

    @Test
    void createDoesNotSaveWhenAccountIsMissing() {
        when(accountService.findByUserId(USER_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertStatus(() -> paymentRequestService.create(USER_ID, new CreatePaymentRequestDto(300L)), HttpStatus.NOT_FOUND);

        verify(paymentRequestRepository, never()).save(any());
        verify(paymentRequestProducer, never()).publishExpiration(any());
    }

    @Test
    void findByIdReturnsActivePaymentRequest() {
        PaymentRequest paymentRequest = paymentRequest();
        when(paymentRequestRepository.findActiveById(org.mockito.ArgumentMatchers.eq(PAYMENT_REQUEST_ID), any(Instant.class)))
            .thenReturn(Optional.of(paymentRequest));

        PaymentRequestResponseDto response = paymentRequestService.findById(PAYMENT_REQUEST_ID);

        assertThat(response).isEqualTo(new PaymentRequestResponseDto(PAYMENT_REQUEST_ID, 300L, account.toResponseDto(), CREATED_AT));
    }

    @Test
    void findByIdThrowsNotFoundWhenMissingOrExpired() {
        when(paymentRequestRepository.findActiveById(org.mockito.ArgumentMatchers.eq(PAYMENT_REQUEST_ID), any(Instant.class)))
            .thenReturn(Optional.empty());

        assertStatus(() -> paymentRequestService.findById(PAYMENT_REQUEST_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteExpiredDeletesOnlyExpiredPaymentRequest() {
        paymentRequestService.deleteExpired(PAYMENT_REQUEST_ID);

        verify(paymentRequestRepository).deleteExpiredById(org.mockito.ArgumentMatchers.eq(PAYMENT_REQUEST_ID), any(Instant.class));
    }

    private PaymentRequest paymentRequest() {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setId(PAYMENT_REQUEST_ID);
        paymentRequest.setAccount(account);
        paymentRequest.setValue(300L);
        paymentRequest.setCreatedAt(CREATED_AT);
        return paymentRequest;
    }

    private static Account account() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("joao@example.com");
        user.setName("Joao Dias");
        user.setCreatedAt(CREATED_AT);

        Account account = new Account();
        account.setId(ACCOUNT_ID);
        account.setUser(user);
        account.setBalance(1000_00L);
        return account;
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(status);
    }
}
