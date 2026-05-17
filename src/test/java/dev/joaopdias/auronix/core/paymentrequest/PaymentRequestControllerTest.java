package dev.joaopdias.auronix.core.paymentrequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.joaopdias.auronix.core.paymentrequest.dto.CreatePaymentRequestDto;
import dev.joaopdias.auronix.core.paymentrequest.dto.PaymentRequestResponseDto;
import dev.joaopdias.auronix.core.user.entities.User;
import dev.joaopdias.auronix.shared.security.AuthenticatedUser;
import dev.joaopdias.auronix.shared.services.SecurityService;

@WebMvcTest(PaymentRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentRequestControllerTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd14");
    private static final Instant CREATED_AT = Instant.parse("2026-05-17T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentRequestService paymentRequestService;

    @MockitoBean
    private SecurityService securityService;

    private static User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUsesAuthenticatedUserAndPayload() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationToken());
        when(paymentRequestService.create(org.mockito.ArgumentMatchers.eq(USER_ID), any(CreatePaymentRequestDto.class)))
            .thenReturn(response());

        mockMvc.perform(post("/payment-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": 300
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(PAYMENT_REQUEST_ID.toString()))
            .andExpect(jsonPath("$.value").value(300))
            .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.createdAt").value("2026-05-17T00:00:00Z"));

        verify(paymentRequestService).create(org.mockito.ArgumentMatchers.eq(USER_ID), any(CreatePaymentRequestDto.class));
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationToken());

        mockMvc.perform(post("/payment-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": 9
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void findByIdReturnsPaymentRequest() throws Exception {
        when(paymentRequestService.findById(PAYMENT_REQUEST_ID)).thenReturn(response());

        mockMvc.perform(get("/payment-request/" + PAYMENT_REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(PAYMENT_REQUEST_ID.toString()))
            .andExpect(jsonPath("$.value").value(300))
            .andExpect(jsonPath("$.user.id").value(USER_ID.toString()));
    }

    private static UsernamePasswordAuthenticationToken authenticationToken() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID), null, List.of());
    }

    private static PaymentRequestResponseDto response() {
        return new PaymentRequestResponseDto(PAYMENT_REQUEST_ID, 300L, user.toResponseDto(), CREATED_AT);
    }
}
