package dev.joaopdias.auronix.core.account;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.joaopdias.auronix.core.account.dto.AccountResponseDto;
import dev.joaopdias.auronix.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/account")
public class AccountController {
    
    @Autowired
    private AccountService accountService;

    @GetMapping()
    public AccountResponseDto findById(@AuthenticationPrincipal AuthenticatedUser authentication) {
        return accountService.findByUserId(authentication.id()).toResponseDto();
    }

    @GetMapping("/email")
    public UUID findIdByUserEmail(@RequestParam("email") String userEmail) {
        return accountService.findIdByUserEmail(userEmail);
    }

}
