package com.artha.api.rest;

import com.artha.api.dto.Dtos.*;
import com.artha.application.command.AccountCommandHandlers;
import com.artha.application.command.AccountCommands.*;
import com.artha.application.query.AccountQueries.*;
import com.artha.application.query.AccountQueryHandlers;
import com.artha.application.query.AccountView;
import com.artha.application.query.TransactionView;
import com.artha.core.resilience.TokenBucketRateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountCommandHandlers commands;
    private final AccountQueryHandlers queries;
    private final TokenBucketRateLimiter rateLimiter;

    public AccountController(AccountCommandHandlers commands,
                             AccountQueryHandlers queries,
                             TokenBucketRateLimiter rateLimiter) {
        this.commands = commands;
        this.queries = queries;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest req,
                                                Authentication auth) {
        requireRate(auth);
        UUID id = commands.handle(new OpenAccount(UUID.randomUUID(), req.ownerId(), req.currency()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new AccountResponse(id));
    }

    @PostMapping("/{accountId}/deposits")
    public ResponseEntity<Void> deposit(@PathVariable UUID accountId,
                                        @Valid @RequestBody DepositRequest req,
                                        Authentication auth) {
        requireRate(auth);
        commands.handle(new Deposit(accountId, req.amount(), req.currency(), req.reference()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{accountId}/withdrawals")
    public ResponseEntity<Void> withdraw(@PathVariable UUID accountId,
                                         @Valid @RequestBody WithdrawRequest req,
                                         Authentication auth) {
        requireRate(auth);
        commands.handle(new Withdraw(accountId, req.amount(), req.currency(), req.reference()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{accountId}/freeze")
    public ResponseEntity<Void> freeze(@PathVariable UUID accountId,
                                       @Valid @RequestBody FreezeRequest req,
                                       Authentication auth) {
        requireRate(auth);
        commands.handle(new FreezeAccount(accountId, req.reason()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{accountId}/unfreeze")
    public ResponseEntity<Void> unfreeze(@PathVariable UUID accountId, Authentication auth) {
        requireRate(auth);
        commands.handle(new UnfreezeAccount(accountId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> close(@PathVariable UUID accountId, Authentication auth) {
        requireRate(auth);
        commands.handle(new CloseAccount(accountId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountView> get(@PathVariable UUID accountId, Authentication auth) {
        requireRate(auth);
        AccountView view = queries.handle(new GetAccount(accountId));
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<List<TransactionView>> transactions(@PathVariable UUID accountId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "50") int size,
                                                              Authentication auth) {
        requireRate(auth);
        return ResponseEntity.ok(queries.handle(new GetTransactionHistory(accountId, page, size)));
    }

    @GetMapping(params = "ownerId")
    public ResponseEntity<List<AccountView>> byOwner(@RequestParam String ownerId, Authentication auth) {
        requireRate(auth);
        return ResponseEntity.ok(queries.handle(new ListAccountsByOwner(ownerId)));
    }

    private void requireRate(Authentication auth) {
        String principal = auth == null ? "anonymous" : auth.getName();
        if (!rateLimiter.tryAcquire(principal)) {
            throw new RateLimitedException();
        }
    }

    public static class RateLimitedException extends RuntimeException {}
}
