package dev.joaopdias.auronix.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import dev.joaopdias.auronix.core.account.AccountService;
import dev.joaopdias.auronix.core.user.dto.AuthResponseDto;
import dev.joaopdias.auronix.core.user.dto.CreateUserDto;
import dev.joaopdias.auronix.core.user.dto.LoginUserDto;
import dev.joaopdias.auronix.core.user.dto.UpdateUserDto;
import dev.joaopdias.auronix.core.user.entities.User;
import dev.joaopdias.auronix.shared.services.SecurityService;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final Instant CREATED_AT = Instant.parse("2026-05-16T12:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = user("joao@example.com", "Joao", "hashed-password");
    }

    @Test
    void createSavesUserBeforeCreatingJwt() {
        CreateUserDto dto = new CreateUserDto("joao@example.com", "Joao", "Password1!");
        when(userRepository.findByEmail(dto.email())).thenReturn(null);
        when(securityService.hashPassword(dto.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(USER_ID);
            saved.setCreatedAt(CREATED_AT);
            return saved;
        });
        when(securityService.createJwt(USER_ID)).thenReturn("jwt-token");

        AuthResponseDto response = userService.create(dto);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        verify(accountService).create(USER_ID);
        verify(securityService).createJwt(USER_ID);
        InOrder inOrder = Mockito.inOrder(userRepository, accountService, securityService);
        inOrder.verify(userRepository).save(any(User.class));
        inOrder.verify(accountService).create(USER_ID);
        inOrder.verify(securityService).createJwt(USER_ID);
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(dto.email());
        assertThat(userCaptor.getValue().getName()).isEqualTo(dto.name());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("hashed-password");
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(USER_ID);
        assertThat(response.user().email()).isEqualTo(dto.email());
    }

    @Test
    void createThrowsBadRequestWhenEmailAlreadyExists() {
        CreateUserDto dto = new CreateUserDto("joao@example.com", "Joao", "Password1!");
        when(userRepository.findByEmail(dto.email())).thenReturn(user);

        assertThatThrownBy(() -> userService.create(dto))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
        verify(securityService, never()).hashPassword(any());
        verify(accountService, never()).create(any());
    }

    @Test
    void loginReturnsTokenAndUserWhenCredentialsMatch() {
        when(userRepository.findByEmail("joao@example.com")).thenReturn(user);
        when(securityService.matchesPassword("Password1!", "hashed-password")).thenReturn(true);
        when(securityService.createJwt(USER_ID)).thenReturn("jwt-token");

        AuthResponseDto response = userService.login(new LoginUserDto("joao@example.com", "Password1!"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(USER_ID);
        assertThat(response.user().email()).isEqualTo("joao@example.com");
    }

    @Test
    void loginThrowsBadRequestWhenEmailDoesNotExist() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(null);

        assertInvalidCredentials(() -> userService.login(new LoginUserDto("missing@example.com", "Password1!")));
        verify(securityService, never()).matchesPassword(any(), any());
    }

    @Test
    void loginThrowsBadRequestWhenPasswordDoesNotMatch() {
        when(userRepository.findByEmail("joao@example.com")).thenReturn(user);
        when(securityService.matchesPassword("Wrong1!", "hashed-password")).thenReturn(false);

        assertInvalidCredentials(() -> userService.login(new LoginUserDto("joao@example.com", "Wrong1!")));
        verify(securityService, never()).createJwt(any());
    }

    @Test
    void decodeTokenFindsUserAndReturnsRenewedToken() {
        when(securityService.decodeJwt("old-token")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(securityService.createJwt(USER_ID)).thenReturn("new-token");

        AuthResponseDto response = userService.decodeToken("old-token");

        assertThat(response.token()).isEqualTo("new-token");
        assertThat(response.user().id()).isEqualTo(USER_ID);
    }

    @Test
    void findByIdReturnsUserWhenFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThat(userService.findById(USER_ID)).isSameAs(user);
    }

    @Test
    void findByIdThrowsNotFoundWhenMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(USER_ID))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateChangesEmailNameAndPassword() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("novo@example.com")).thenReturn(null);
        when(securityService.hashPassword("Newpass1!")).thenReturn("new-hash");

        userService.update(USER_ID, new UpdateUserDto("novo@example.com", "Novo Nome", "Newpass1!"));

        assertThat(user.getEmail()).isEqualTo("novo@example.com");
        assertThat(user.getName()).isEqualTo("Novo Nome");
        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    void updateIgnoresNullFields() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.update(USER_ID, new UpdateUserDto(null, null, null));

        assertThat(user.getEmail()).isEqualTo("joao@example.com");
        assertThat(user.getName()).isEqualTo("Joao");
        assertThat(user.getPassword()).isEqualTo("hashed-password");
        verify(userRepository).save(user);
        verify(userRepository, never()).findByEmail(any());
        verify(securityService, never()).hashPassword(any());
    }

    @Test
    void updateDoesNotValidateSameEmail() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.update(USER_ID, new UpdateUserDto("joao@example.com", null, null));

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository).save(user);
    }

    @Test
    void updateThrowsBadRequestWhenNewEmailAlreadyExists() {
        User otherUser = user("novo@example.com", "Outro", "hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("novo@example.com")).thenReturn(otherUser);

        assertThatThrownBy(() -> userService.update(USER_ID, new UpdateUserDto("novo@example.com", null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteRemovesExistingUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.delete(USER_ID);

        verify(userRepository).delete(user);
    }

    private static void assertInvalidCredentials(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static User user(String email, String name, String password) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(email);
        user.setName(name);
        user.setPassword(password);
        user.setCreatedAt(CREATED_AT);
        return user;
    }
}
