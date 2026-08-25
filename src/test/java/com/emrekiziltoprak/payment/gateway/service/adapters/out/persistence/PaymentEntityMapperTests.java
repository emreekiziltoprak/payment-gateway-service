package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.PaymentEntity;
import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEntityMapperTests {

    @Test
    void preservesGatewayReferenceDuringPersistenceRoundTrip() {
        Payment payment = new Payment(
                PaymentId.generate(),
                AccountId.generate(),
                AccountId.generate(),
                "pi_test_123",
                new Money(new BigDecimal("25.00"), Currency.getInstance("TRY")),
                PaymentProvider.STRIPE,
                PaymentStatus.PENDING
        );

        PaymentEntity entity = PaymentEntity.fromDomain(payment);
        Payment restoredPayment = entity.toDomain();

        assertThat(entity.getReferenceId()).isEqualTo("pi_test_123");
        assertThat(restoredPayment.getReferenceId()).isEqualTo("pi_test_123");
        assertThat(restoredPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
