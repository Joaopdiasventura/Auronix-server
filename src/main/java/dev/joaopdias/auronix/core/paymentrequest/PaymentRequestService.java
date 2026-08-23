package dev.joaopdias.auronix.core.paymentrequest;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import dev.joaopdias.auronix.core.account.AccountService;
import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.paymentrequest.dto.CreatePaymentRequestDto;
import dev.joaopdias.auronix.core.paymentrequest.dto.PaymentRequestResponseDto;
import dev.joaopdias.auronix.core.paymentrequest.entities.PaymentRequest;
import dev.joaopdias.auronix.core.paymentrequest.events.PaymentRequestExpirationEvent;

@Service
public class PaymentRequestService {
    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Autowired
    private PaymentRequestProducer paymentRequestProducer;

    @Autowired
    private AccountService accountService;

    @Transactional
    public PaymentRequestResponseDto create(UUID userId, CreatePaymentRequestDto createPaymentRequestDto) {
        Account account = accountService.findByUserId(userId);

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setAccount(account);
        paymentRequest.setValue(createPaymentRequestDto.value());

        paymentRequest = paymentRequestRepository.save(paymentRequest);

        paymentRequestProducer.publishExpiration(new PaymentRequestExpirationEvent(UUID.randomUUID(), paymentRequest.getId()));

        return paymentRequest.toResponseDto();
    }

    @Transactional(readOnly = true)
    public PaymentRequestResponseDto findById(UUID id) {
        return paymentRequestRepository.findActiveById(id, Instant.now())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cobranca nao encontrada"))
            .toResponseDto();
    }

    @Transactional
    public void deleteExpired(UUID id) {
        paymentRequestRepository.deleteExpiredById(id, Instant.now());
    }
}
