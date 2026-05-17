package dev.joaopdias.auronix.core.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.joaopdias.auronix.core.transaction.dto.CreateTransferDto;
import dev.joaopdias.auronix.shared.security.AuthenticatedUser;
import dev.joaopdias.auronix.shared.services.SecurityService;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID PAYEE_ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd13");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private SecurityService securityService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUsesAuthenticatedUserAndPayload() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationToken());

        mockMvc.perform(post("/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "payeeAccountId": "%s",
                      "amount": 300
                    }
                    """.formatted(PAYEE_ACCOUNT_ID)))
            .andExpect(status().isOk());

        verify(transactionService).create(org.mockito.ArgumentMatchers.eq(USER_ID), any(CreateTransferDto.class));
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationToken());

        mockMvc.perform(post("/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "amount": 10
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    private static UsernamePasswordAuthenticationToken authenticationToken() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID), null, List.of());
    }
}
