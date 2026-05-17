package dev.joaopdias.auronix.shared.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

class SecurityServiceTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");

    private SecurityService securityService;

    @BeforeEach
    void setUp() {
        securityService = securityService(120);
    }

    @Test
    void hashPasswordCreatesHashThatMatchesRawPassword() {
        String hash = securityService.hashPassword("Password1!");

        assertThat(hash).isNotEqualTo("Password1!");
        assertThat(securityService.matchesPassword("Password1!", hash)).isTrue();
        assertThat(securityService.matchesPassword("Wrong1!", hash)).isFalse();
    }

    @Test
    void createJwtAndDecodeJwtRoundTripUserId() {
        String token = securityService.createJwt(USER_ID);

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(securityService.decodeJwt(token)).isEqualTo(USER_ID);
    }

    @Test
    void decodeJwtThrowsWhenTokenIsMalformed() {
        assertThatThrownBy(() -> securityService.decodeJwt("invalid-token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Token JWT inválido.");
    }

    @Test
    void decodeJwtThrowsWhenSignatureIsInvalid() {
        String token = securityService.createJwt(USER_ID);
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "." + flipLastCharacter(parts[2]);

        assertThatThrownBy(() -> securityService.decodeJwt(tamperedToken))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Assinatura do token JWT inválida.");
    }

    @Test
    void decodeJwtThrowsWhenTokenIsExpired() {
        SecurityService expiredTokenService = securityService(-1);
        String token = expiredTokenService.createJwt(USER_ID);

        assertThatThrownBy(() -> expiredTokenService.decodeJwt(token))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Token JWT expirado.");
    }

    @Test
    void createJwtWrapsUnexpectedFailures() {
        SecurityService brokenService = new SecurityService("test-secret", 120);
        ReflectionTestUtils.setField(brokenService, "objectMapper", null);

        assertThatThrownBy(() -> brokenService.createJwt(USER_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Não foi possível criar o token JWT.");
    }

    @Test
    void decodeJwtWrapsInvalidPayload() throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"typ\":\"JWT\",\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String invalidPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("not-json".getBytes(StandardCharsets.UTF_8));
        String unsignedToken = header + "." + invalidPayload;
        String signature = sign(unsignedToken);

        assertThatThrownBy(() -> securityService.decodeJwt(unsignedToken + "." + signature))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Token JWT inválido.");
    }

    private static SecurityService securityService(long expiresInMinutes) {
        SecurityService service = new SecurityService("test-secret", expiresInMinutes);
        ReflectionTestUtils.setField(service, "objectMapper", new JsonMapper());
        return service;
    }

    private static String flipLastCharacter(String value) {
        char replacement = value.charAt(value.length() - 1) == 'a' ? 'b' : 'a';
        return value.substring(0, value.length() - 1) + replacement;
    }

    private static String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }
}
