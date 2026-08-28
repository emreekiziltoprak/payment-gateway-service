package com.emrekiziltoprak.payment.gateway.service.domain.valueobject;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.CurrencyMismatchException;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.InvalidMoneyException;


class MoneyTest {

    @Test
    void should_normalize_different_scales_and_evaluate_as_equal() {
        Money tenPointZero = Money.of(new BigDecimal("10.0"), "USD");
        Money tenPointZeroZero = Money.of(new BigDecimal("10.00"), "USD");

        assertThat(tenPointZero).isEqualTo(tenPointZeroZero);
        assertThat(tenPointZero.amount()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void should_perform_addition_and_return_new_immutable_instance() {
        Money base = Money.of(new BigDecimal("10.00"), "USD");
        Money addition = Money.of(new BigDecimal("5.00"), "USD");

        Money result = base.add(addition);

        assertThat(result.amount()).isEqualTo(new BigDecimal("15.00"));
        assertThat(result).isNotSameAs(base);
        assertThat(result).isNotSameAs(addition);
    }

    @Test
    void should_subtract_to_zero_correctly() {
        Money base = Money.of(new BigDecimal("10.00"), "USD");
        Money deduction = Money.of(new BigDecimal("10.00"), "USD");

        Money result = base.subtract(deduction);

        assertThat(result).isEqualTo(Money.zero("USD"));
        assertThat(result.amount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.isPositive()).isFalse();
    }

    @Test
    void should_reject_cross_currency_arithmetic() {
        Money usd = Money.of(new BigDecimal("10.00"), "USD");
        Money eur = Money.of(new BigDecimal("5.00"), "EUR");

        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("Currency mismatch: USD != EUR");

        assertThatThrownBy(() -> usd.subtract(eur))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void should_reject_cross_currency_comparisons() {
        Money usd = Money.of(new BigDecimal("10.00"), "USD");
        Money eur = Money.of(new BigDecimal("5.00"), "EUR");

        assertThatThrownBy(() -> usd.isGreaterThan(eur))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void should_reject_negative_amounts_at_creation() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-5.00"), "USD"))
                .isInstanceOf(InvalidMoneyException.class)
                .hasMessageContaining("Amount must be non-negative");
    }

    @Test
    void should_allow_zero_valued_money() {
        Money zero = Money.zero("USD");
        
        assertThat(zero.amount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(zero.isPositive()).isFalse();
    }

    @Test
    void should_reject_precision_loss_for_usd() {
        // USD allows 2 fraction digits. Providing 3 should throw InvalidMoneyException.
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.125"), "USD"))
                .isInstanceOf(InvalidMoneyException.class)
                .hasMessageContaining("Cannot safely represent 10.125 without data loss");
    }

    @Test
    void should_handle_zero_fraction_currencies_like_jpy() {
        Money jpy = Money.of(new BigDecimal("500"), "JPY");
        
        assertThat(jpy.amount()).isEqualTo(new BigDecimal("500"));

        assertThatThrownBy(() -> Money.of(new BigDecimal("500.5"), "JPY"))
                .isInstanceOf(InvalidMoneyException.class)
                .hasMessageContaining("Cannot safely represent 500.5 without data loss");
    }
}