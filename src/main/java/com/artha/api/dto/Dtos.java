package com.artha.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public final class Dtos {

    private Dtos() {}

    public record OpenAccountRequest(
            @NotBlank @Size(max = 128) String ownerId,
            @NotBlank @Size(min = 3, max = 3) String currency) {}

    public record AccountResponse(UUID accountId) {}

    public record DepositRequest(
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Size(max = 256) String reference) {}

    public record WithdrawRequest(
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Size(max = 256) String reference) {}

    public record TransferRequest(
            @NotNull UUID sourceAccountId,
            @NotNull UUID destinationAccountId,
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Size(max = 256) String reference) {}

    public record TransferResponse(UUID transferId) {}

    public record FreezeRequest(@NotBlank String reason) {}

    public record LoginRequest(@NotBlank String userId, @NotBlank String password) {}
    public record LoginResponse(String token) {}

    public record ErrorResponse(String code, String message) {}
}
