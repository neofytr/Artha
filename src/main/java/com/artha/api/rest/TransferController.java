package com.artha.api.rest;

import com.artha.api.dto.Dtos.*;
import com.artha.application.command.AccountCommandHandlers;
import com.artha.application.command.AccountCommands.TransferMoney;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final AccountCommandHandlers commands;

    public TransferController(AccountCommandHandlers commands) {
        this.commands = commands;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest req) {
        UUID transferId = commands.handle(new TransferMoney(
                req.sourceAccountId(), req.destinationAccountId(),
                req.amount(), req.currency(), req.reference()
        ));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TransferResponse(transferId));
    }
}
