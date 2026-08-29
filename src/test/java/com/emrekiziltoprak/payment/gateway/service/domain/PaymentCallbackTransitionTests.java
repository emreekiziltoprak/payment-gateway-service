package com.emrekiziltoprak.payment.gateway.service.domain;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentFailed;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentSucceeded;
import org.junit.jupiter.api.Test;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPendingPayment;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentCallbackTransitionTests {

    @Test
    void pendingPaymentCanBeMarkedAsSucceeded() {
        Payment payment = aPendingPayment();

        payment.markAsSucceeded();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getDomainEvents())
                .singleElement()
                .isInstanceOf(PaymentSucceeded.class);
    }

    @Test
    void pendingPaymentCanBeMarkedAsFailed() {
        Payment payment = aPendingPayment();

        payment.markAsFailed("insufficient funds");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getDomainEvents())
                .singleElement()
                .isInstanceOfSatisfying(PaymentFailed.class,
                        event -> assertThat(event.reason()).isEqualTo("insufficient funds"));
    }
}
