package dev.joaopdias.auronix.core.account;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.user.entities.User;
import dev.joaopdias.auronix.shared.security.AuthenticatedUser;
import dev.joaopdias.auronix.shared.services.SecurityService;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd11");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private SecurityService securityService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findByIdReturnsAuthenticatedUserAccountAsJson() throws Exception {
        Account account = account();
        when(accountService.findByUserId(USER_ID)).thenReturn(account);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken());

        mockMvc.perform(get("/account"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
            .andExpect(jsonPath("$.balance").value(100000))
            .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.user.email").value("joao@example.com"));
    }

    @Test
    void findIdByUserEmailReturnsAccountId() throws Exception {
        when(accountService.findIdByUserEmail("joao@example.com")).thenReturn(ACCOUNT_ID);

        mockMvc.perform(get("/account/email")
                .param("email", "joao@example.com"))
            .andExpect(status().isOk())
            .andExpect(content().string("\"" + ACCOUNT_ID + "\""));
    }

    private static UsernamePasswordAuthenticationToken authenticationToken() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID), null, List.of());
    }

    private static Account account() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("joao@example.com");
        user.setName("Joao");
        user.setPassword("hashed-password");

        Account account = new Account();
        account.setId(ACCOUNT_ID);
        account.setUser(user);
        account.setBalance(1000_00L);
        return account;
    }
}
