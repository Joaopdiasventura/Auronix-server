package dev.joaopdias.auronix.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import dev.joaopdias.auronix.shared.services.SecurityService;
import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");

    @Mock
    private SecurityService securityService;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter();
        ReflectionTestUtils.setField(filter, "securityService", securityService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternalContinuesWithoutAuthenticationWhenCookieIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(securityService, never()).decodeJwt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilterInternalAuthenticatesWhenCookieIsValid() throws Exception {
        when(securityService.decodeJwt("valid-token")).thenReturn(USER_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user");
        request.setCookies(new Cookie("access_token", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser(USER_ID));
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterInternalRejectsInvalidCookieAndStopsChain() throws Exception {
        when(securityService.decodeJwt("invalid-token")).thenThrow(new IllegalArgumentException("invalid"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user");
        request.setCookies(new Cookie("access_token", "invalid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"Faça login novamente.\"}");
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldNotFilterSkipsOptionsAndPublicPostRoutes() {
        assertThat(filter.shouldNotFilter(request(HttpMethod.OPTIONS, "/anything"))).isTrue();
        assertThat(filter.shouldNotFilter(request(HttpMethod.POST, "/user"))).isTrue();
        assertThat(filter.shouldNotFilter(request(HttpMethod.POST, "/user/login"))).isTrue();
    }

    @Test
    void shouldNotFilterDoesNotSkipProtectedRoutes() {
        assertThat(filter.shouldNotFilter(request(HttpMethod.GET, "/user"))).isFalse();
        assertThat(filter.shouldNotFilter(request(HttpMethod.PATCH, "/user"))).isFalse();
        assertThat(filter.shouldNotFilter(request(HttpMethod.POST, "/other"))).isFalse();
    }

    private static MockHttpServletRequest request(HttpMethod method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method.name(), path);
        request.setServletPath(path);
        return request;
    }
}
