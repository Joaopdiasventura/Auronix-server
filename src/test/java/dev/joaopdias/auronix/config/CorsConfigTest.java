package dev.joaopdias.auronix.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {
    @Test
    void corsConfigurationUsesDefaultOriginWhenPropertyIsDefault() {
        CorsConfiguration config = corsConfiguration("http://localhost:4200");

        assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:4200");
        assertCommonCorsConfiguration(config);
    }

    @Test
    void corsConfigurationTrimsAndIgnoresBlankOrigins() {
        CorsConfiguration config = corsConfiguration("http://localhost:4200; https://app.example.com ; ;");

        assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:4200", "https://app.example.com");
        assertCommonCorsConfiguration(config);
    }

    private static CorsConfiguration corsConfiguration(String allowedOrigins) {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", allowedOrigins);
        UrlBasedCorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/user"));
        assertThat(config).isNotNull();
        return config;
    }

    private static void assertCommonCorsConfiguration(CorsConfiguration config) {
        assertThat(config.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(config.getAllowedHeaders()).containsExactly("*");
        assertThat(config.getExposedHeaders()).containsExactly("Authorization", "Content-Disposition");
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getMaxAge()).isEqualTo(3600L);
    }
}
