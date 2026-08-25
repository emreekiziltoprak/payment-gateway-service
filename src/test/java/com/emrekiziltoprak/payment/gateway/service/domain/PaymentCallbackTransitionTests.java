package com.emrekiziltoprak.payment.gateway.service.domain;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentFailed;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentSucceeded;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCallbackTransitionTests {

    @Test
    void pendingPaymentCanBeMarkedAsSucceeded() {
        Payment payment = pendingPayment();

        payment.markAsSucceeded();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getDomainEvents())
                .singleElement()
                .isInstanceOf(PaymentSucceeded.class);
    }

    @Test
    void pendingPaymentCanBeMarkedAsFailed() {
        Payment payment = pendingPayment();

        payment.markAsFailed("insufficient funds");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getDomainEvents())
                .singleElement()
                .isInstanceOfSatisfying(PaymentFailed.class,
                        event -> assertThat(event.reason()).isEqualTo("insufficient funds"));
    }

    private Payment pendingPayment() {
        return new Payment(
                PaymentId.generate(),
                AccountId.generate(),
                AccountId.generate(),
                "pi_test_123",
                new Money(new BigDecimal("25.00"), Currency.getInstance("TRY")),
                PaymentProvider.STRIPE,
                PaymentStatus.PENDING
        );
    }
}
