package dev.joaopdias.auronix.core.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import dev.joaopdias.auronix.core.account.entities.Account;
import dev.joaopdias.auronix.core.user.UserRepository;
import dev.joaopdias.auronix.core.user.entities.User;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    private static final UUID USER_ID = UUID.fromString("019b1f0d-9b5c-7c5f-9a57-34e2d66fbd10");
    private static final UUID ACCOUNT_ID = UUID.fromString("019b1f0d-9b5c-76ab-9a57-34e2d66fbd11");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setEmail("joao@example.com");

        account = new Account();
        account.setId(ACCOUNT_ID);
        account.setUser(user);
        account.setBalance(1000_00L);
    }

    @Test
    void createCreatesAccountWithInitialBalance() {
        when(accountRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account created = accountService.create(USER_ID);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(created.getUser()).isSameAs(user);
        assertThat(created.getBalance()).isEqualTo(1000_00L);
        assertThat(accountCaptor.getValue().getUser()).isSameAs(user);
        assertThat(accountCaptor.getValue().getBalance()).isEqualTo(1000_00L);
    }

    @Test
    void createThrowsBadRequestWhenUserAlreadyHasAccount() {
        when(accountRepository.existsByUserId(USER_ID)).thenReturn(true);

        assertStatus(() -> accountService.create(USER_ID), HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).findById(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createThrowsNotFoundWhenUserDoesNotExist() {
        when(accountRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertStatus(() -> accountService.create(USER_ID), HttpStatus.NOT_FOUND);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void findByUserIdReturnsAccount() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        assertThat(accountService.findByUserId(USER_ID)).isSameAs(account);
    }

    @Test
    void findByUserIdThrowsNotFoundWhenMissing() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertStatus(() -> accountService.findByUserId(USER_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void findByIdReturnsAccount() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThat(accountService.findById(ACCOUNT_ID)).isSameAs(account);
    }

    @Test
    void findByIdThrowsNotFoundWhenMissing() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertStatus(() -> accountService.findById(ACCOUNT_ID), HttpStatus.NOT_FOUND);
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(status);
    }
}
