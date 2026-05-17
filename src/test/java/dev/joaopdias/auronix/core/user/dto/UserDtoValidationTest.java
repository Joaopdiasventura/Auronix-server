package dev.joaopdias.auronix.core.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class UserDtoValidationTest {
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
    void createUserDtoAcceptsValidPayload() {
        assertThat(validate(new CreateUserDto("joao@example.com", "Joao", "Password1!"))).isEmpty();
    }

    @Test
    void createUserDtoRejectsInvalidFields() {
        Set<ConstraintViolation<CreateUserDto>> violations = validate(
            new CreateUserDto("invalid-email", "", "weak")
        );

        assertThat(properties(violations)).contains("email", "name", "password");
    }

    @Test
    void createUserDtoRejectsTooLongEmailNameAndPassword() {
        Set<ConstraintViolation<CreateUserDto>> violations = validate(
            new CreateUserDto("a".repeat(321) + "@example.com", "a".repeat(151), "A".repeat(73) + "1!")
        );

        assertThat(properties(violations)).contains("email", "name", "password");
    }

    @Test
    void createUserDtoRejectsPasswordWithoutRequiredCharacterClasses() {
        Set<ConstraintViolation<CreateUserDto>> violations = validate(
            new CreateUserDto("joao@example.com", "Joao", "password")
        );

        assertThat(properties(violations)).contains("password");
    }

    @Test
    void loginUserDtoAcceptsValidPayload() {
        assertThat(validate(new LoginUserDto("joao@example.com", "Password1!"))).isEmpty();
    }

    @Test
    void loginUserDtoRejectsInvalidEmailAndPassword() {
        Set<ConstraintViolation<LoginUserDto>> violations = validate(new LoginUserDto("", "weak"));

        assertThat(properties(violations)).contains("email", "password");
    }

    @Test
    void updateUserDtoAllowsAllFieldsNull() {
        assertThat(validate(new UpdateUserDto(null, null, null))).isEmpty();
    }

    @Test
    void updateUserDtoRejectsInvalidProvidedFields() {
        Set<ConstraintViolation<UpdateUserDto>> violations = validate(
            new UpdateUserDto("invalid-email", "a", "weak")
        );

        assertThat(properties(violations)).contains("email", "name", "password");
    }

    @Test
    void updateUserDtoRejectsProvidedValuesAboveMaxSizes() {
        Set<ConstraintViolation<UpdateUserDto>> violations = validate(
            new UpdateUserDto("a".repeat(321) + "@example.com", "a".repeat(151), "A".repeat(73) + "1!")
        );

        assertThat(properties(violations)).contains("email", "name", "password");
    }

    private static <T> Set<ConstraintViolation<T>> validate(T dto) {
        return validator.validate(dto);
    }

    private static Set<String> properties(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }
}
