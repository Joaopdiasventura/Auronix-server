package dev.joaopdias.auronix.core.paymentrequest.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

class CreatePaymentRequestDtoValidationTest {
    private final Validator validator = validator();

    @Test
    void validDtoHasNoViolations() {
        Set<ConstraintViolation<CreatePaymentRequestDto>> violations = validator.validate(new CreatePaymentRequestDto(1000L));

        assertThat(violations).isEmpty();
    }

    @Test
    void valueMustBeAtLeastTenCents() {
        Set<ConstraintViolation<CreatePaymentRequestDto>> violations = validator.validate(new CreatePaymentRequestDto(9L));

        assertThat(violations).extracting(ConstraintViolation::getMessage)
            .contains("Digite um valor de pelo menos dez centavos");
    }

    @Test
    void valueMustBeLessThanOneMillionReais() {
        Set<ConstraintViolation<CreatePaymentRequestDto>> violations = validator.validate(new CreatePaymentRequestDto(1000000_01L));

        assertThat(violations).extracting(ConstraintViolation::getMessage)
            .contains("Digite um valor menor que um milhão de reais");
    }

    private static Validator validator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
    }
}
