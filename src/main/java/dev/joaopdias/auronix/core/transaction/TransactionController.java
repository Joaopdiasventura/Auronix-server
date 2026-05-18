package dev.joaopdias.auronix.core.transaction;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.joaopdias.auronix.core.transaction.dto.CreateTransferDto;
import dev.joaopdias.auronix.core.transaction.dto.TransactionResponseDto;
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

    @GetMapping()
    public Page<TransactionResponseDto> findMany(
        @AuthenticationPrincipal AuthenticatedUser authentication,
        Pageable pageable
    ) {
        return transactionService.findByUserId(authentication.id(), pageable);
    }

    @GetMapping("/{id}")
    public TransactionResponseDto findById(
        @AuthenticationPrincipal AuthenticatedUser authentication,
        @PathVariable UUID id
    ) {
        return transactionService.findById(authentication.id(), id);
    }
}
