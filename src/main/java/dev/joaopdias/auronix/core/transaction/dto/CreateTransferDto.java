package dev.joaopdias.auronix.core.transaction.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateTransferDto(
    @NotNull(message = "A conta de destino é obrigatória.")
    UUID payeeAccountId,

    @Min(value = 11, message = "O valor da transferência deve ser maior que dez centavos.")
    long amount
) {
}