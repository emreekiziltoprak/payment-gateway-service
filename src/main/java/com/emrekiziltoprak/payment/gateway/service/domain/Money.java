package com.emrekiziltoprak.payment.gateway.service.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

import com.emrekiziltoprak.payment.gateway.service.domain.exception.CurrencyMismatchException;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.InvalidMoneyException;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidMoneyException("Amount must be non-negative");
        }

        int defaultFractionDigits = currency.getDefaultFractionDigits();

        try {
            amount = amount.setScale(defaultFractionDigits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidMoneyException(
                String.format("Invalid precision for currency %s. Cannot safely represent %s without data loss.", 
                              currency.getCurrencyCode(), amount)
            );
        }
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)));
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        checkCurrencyMatch(other);
        return new Money(this.amount.add(other.amount()), this.currency);
    }

    public Money subtract(Money other) {
        checkCurrencyMatch(other);
        return new Money(this.amount.subtract(other.amount()), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        checkCurrencyMatch(other);
        return this.amount.compareTo(other.amount()) > 0;
    }
    
    public boolean isLessThanOrEqual(Money other) {
        checkCurrencyMatch(other);
        return this.amount.compareTo(other.amount()) <= 0;
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private void checkCurrencyMatch(Money other) {
        if (!this.currency.equals(other.currency())) {
            throw new CurrencyMismatchException(
                String.format("Currency mismatch: %s != %s", 
                              this.currency.getCurrencyCode(), 
                              other.currency().getCurrencyCode())
            );
        }
    }
}
