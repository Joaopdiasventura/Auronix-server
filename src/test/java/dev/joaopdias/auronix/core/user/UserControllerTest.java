package dev.joaopdias.auronix.core.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import dev.joaopdias.auronix.core.user.dto.AuthResponseDto;
import dev.joaopdias.auronix.core.user.dto.CreateUserDto;
import dev.joaopdias.auronix.core.user.dto.LoginUserDto;
import dev.joaopdias.auronix.core.user.dto.UpdateUserDto;
import dev.joaopdias.auronix.core.user.dto.UserResponseDto;
import dev.joaopdias.auronix.shared.security.AuthenticatedUser;
import dev.joaopdias.auronix.shared.services.SecurityService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final Instant CREATED_AT = Instant.parse("2026-05-16T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SecurityService securityService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReturnsUserAndSetsAccessTokenCookie() throws Exception {
        when(userService.create(any(CreateUserDto.class))).thenReturn(authResponse("created-token"));

        mockMvc.perform(post("/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "joao@example.com",
                      "name": "Joao",
                      "password": "Password1!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().value("access_token", "created-token"))
            .andExpect(cookie().httpOnly("access_token", true))
            .andExpect(jsonPath("$.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.email").value("joao@example.com"))
            .andExpect(jsonPath("$.name").value("Joao"));
    }

    @Test
    void loginReturnsUserAndSetsAccessTokenCookie() throws Exception {
        when(userService.login(any(LoginUserDto.class))).thenReturn(authResponse("login-token"));

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "joao@example.com",
                      "password": "Password1!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().value("access_token", "login-token"))
            .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    void decodeTokenReadsCookieRenewsCookieAndReturnsUser() throws Exception {
        when(userService.decodeToken("old-token")).thenReturn(authResponse("new-token"));

        mockMvc.perform(get("/user").cookie(new jakarta.servlet.http.Cookie("access_token", "old-token")))
            .andExpect(status().isOk())
            .andExpect(cookie().value("access_token", "new-token"))
            .andExpect(jsonPath("$.email").value("joao@example.com"));
    }

    @Test
    void updateUsesAuthenticatedUserPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationToken());

        mockMvc.perform(patch("/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "novo@example.com",
                      "name": "Novo Nome",
                      "password": "Password1!"
                    }
                    """))
            .andExpect(status().isOk());

        verify(userService).update(org.mockito.ArgumentMatchers.eq(USER_ID), any(UpdateUserDto.class));
    }

    @Test
    void deleteUsesAuthenticatedUserPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationToken());

        mockMvc.perform(delete("/user"))
            .andExpect(status().isOk());

        verify(userService).delete(USER_ID);
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-email",
                      "name": "",
                      "password": "weak"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void loginRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "",
                      "password": "weak"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    private static AuthResponseDto authResponse(String token) {
        UserResponseDto user = new UserResponseDto(USER_ID, "joao@example.com", "Joao", CREATED_AT);
        return new AuthResponseDto(token, user);
    }

    private static UsernamePasswordAuthenticationToken authenticationToken() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID), null, List.of());
    }
}
