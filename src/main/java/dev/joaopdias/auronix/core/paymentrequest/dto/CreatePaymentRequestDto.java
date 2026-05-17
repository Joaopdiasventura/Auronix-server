package dev.joaopdias.auronix.core.paymentrequest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreatePaymentRequestDto(
    @Min(value = 10, message = "Digite um valor de pelo menos dez centavos")
    @Max(value = 1000000_00, message = "Digite um valor menor que um milhão de reais")
    long value
) {
}
