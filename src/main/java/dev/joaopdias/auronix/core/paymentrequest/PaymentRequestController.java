package dev.joaopdias.auronix.core.paymentrequest;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.joaopdias.auronix.core.paymentrequest.dto.CreatePaymentRequestDto;
import dev.joaopdias.auronix.core.paymentrequest.dto.PaymentRequestResponseDto;
import dev.joaopdias.auronix.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/payment-request")
public class PaymentRequestController {
    @Autowired
    private PaymentRequestService paymentRequestService;

    @PostMapping()
    public PaymentRequestResponseDto create(
        @AuthenticationPrincipal AuthenticatedUser authentication,
        @RequestBody @Valid CreatePaymentRequestDto createPaymentRequestDto
    ) {
        return paymentRequestService.create(authentication.id(), createPaymentRequestDto);
    }

    @GetMapping("/{id}")
    public PaymentRequestResponseDto findById(@PathVariable UUID id) {
        return paymentRequestService.findById(id);
    }
}
