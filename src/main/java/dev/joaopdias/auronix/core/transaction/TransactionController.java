package dev.joaopdias.auronix.core.transaction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.joaopdias.auronix.core.transaction.dto.CreateTransferDto;
import dev.joaopdias.auronix.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/transaction")
public class TransactionController {
    @Autowired
    private TransactionService transactionService;

    @PostMapping()
    public void create(
        @AuthenticationPrincipal AuthenticatedUser authentication,
        @RequestBody() @Valid CreateTransferDto createTransferDto
    ) {
        transactionService.create(authentication.id(), createTransferDto);
    }
}
