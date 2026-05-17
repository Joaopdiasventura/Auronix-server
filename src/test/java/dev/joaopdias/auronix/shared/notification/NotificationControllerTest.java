package dev.joaopdias.auronix.shared.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.joaopdias.auronix.shared.security.AuthenticatedUser;
import dev.joaopdias.auronix.shared.services.SecurityService;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseRegistryService sseRegistryService;

    @MockitoBean
    private SecurityService securityService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void streamRegistersAuthenticatedUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationToken());
        when(sseRegistryService.register(USER_ID)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/notifications/stream"))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());

        verify(sseRegistryService).register(USER_ID);
    }

    private static UsernamePasswordAuthenticationToken authenticationToken() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID), null, List.of());
    }
}
