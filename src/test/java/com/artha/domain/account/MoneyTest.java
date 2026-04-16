package com.artha.domain.account;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void scalesToCurrencyFractionDigits() {
        Money usd = Money.of("10.1", "USD");
        assertThat(usd.amount()).isEqualTo(new BigDecimal("10.10"));
    }

    @Test
    void addAndSubtract() {
        Money a = Money.of("10.50", "INR");
        Money b = Money.of("5.25", "INR");
        assertThat(a.add(b)).isEqualTo(Money.of("15.75", "INR"));
        assertThat(a.subtract(b)).isEqualTo(Money.of("5.25", "INR"));
    }

    @Test
    void mixedCurrenciesThrow() {
        Money inr = Money.of("10", "INR");
        Money usd = Money.of("10", "USD");
        assertThatThrownBy(() -> inr.add(usd)).isInstanceOf(Money.CurrencyMismatchException.class);
    }

    @Test
    void zeroIsZeroRegardlessOfScale() {
        assertThat(Money.zero(Currency.getInstance("USD")).isZero()).isTrue();
        assertThat(Money.of("0.00", "USD").isZero()).isTrue();
    }
}
