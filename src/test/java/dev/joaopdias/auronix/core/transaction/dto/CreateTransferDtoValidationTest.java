package dev.joaopdias.auronix.core.transaction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateTransferDtoValidationTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void beforeAll() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void afterAll() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidPayload() {
        CreateTransferDto dto = new CreateTransferDto(UUID.randomUUID(), 11L);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void rejectsMissingPayeeAccount() {
        Set<ConstraintViolation<CreateTransferDto>> violations = validator.validate(new CreateTransferDto(null, 11L));

        assertThat(properties(violations)).contains("payeeAccountId");
    }

    @Test
    void rejectsAmountBelowMinimum() {
        Set<ConstraintViolation<CreateTransferDto>> violations = validator.validate(new CreateTransferDto(UUID.randomUUID(), 10L));

        assertThat(properties(violations)).contains("amount");
    }

    private static Set<String> properties(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }
}
