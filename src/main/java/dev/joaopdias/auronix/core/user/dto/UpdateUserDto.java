package dev.joaopdias.auronix.core.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserDto(
    @Email(message = "Informe um e-mail válido")
    @Size(max = 320, message = "O e-mail deve ter no máximo 320 caracteres")
    String email,

    @Size(min = 2, max = 150, message = "O nome deve ter entre 2 e 150 caracteres")
    String name,

    @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._#\\-])[A-Za-z\\d@$!%*?&._#\\-]+$",
        message = "A senha deve conter ao menos uma letra maiúscula, uma minúscula, um número e um caractere especial"
    )
    String password
) {
}