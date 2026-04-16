package com.artha.application.query;

import com.artha.core.cqrs.Query;

import java.util.List;
import java.util.UUID;

public final class AccountQueries {

    private AccountQueries() {}

    public record GetAccount(UUID accountId) implements Query<AccountView> {}

    public record ListAccountsByOwner(String ownerId) implements Query<List<AccountView>> {}

    public record GetTransactionHistory(UUID accountId, int page, int size)
            implements Query<List<TransactionView>> {}
}
