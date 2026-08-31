package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.PaymentEntity;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentCancellationReason;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPayment;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentEntityMapperTests {

    @Test
    void preservesCancellationReasonAcrossPersistenceMapping() {
        Payment payment = aPayment()
                .withStatus(PaymentStatus.AUTHORIZED)
                .buildRestored();
        payment.markAsCancelled(PaymentCancellationReason.AUTHORIZATION_EXPIRED);

        PaymentEntity entity = PaymentEntity.fromDomain(payment);
        Payment restored = entity.toDomain();

        assertThat(entity.getCancellationReason())
                .isEqualTo(PaymentCancellationReason.AUTHORIZATION_EXPIRED);
        assertThat(restored.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(restored.getCancellationReason())
                .isEqualTo(PaymentCancellationReason.AUTHORIZATION_EXPIRED);
        assertThat(restored.getDomainEvents()).isEmpty();
    }

    @Test
    void preservesDomainAndProviderFailureCodesAcrossPersistenceMapping() {
        Payment payment = aPayment().withStatus(PaymentStatus.PROCESSING).buildRestored();
        payment.markAsFailed(PaymentFailure.fromProvider(
                PaymentFailureCode.DECLINED,
                "card_declined",
                "insufficient_funds",
                "Your card has insufficient funds."
        ));

        PaymentEntity entity = PaymentEntity.fromDomain(payment);
        Payment restored = entity.toDomain();

        assertThat(entity.getFailureCode()).isEqualTo(PaymentFailureCode.DECLINED);
        assertThat(entity.getProviderErrorCode()).isEqualTo("card_declined");
        assertThat(entity.getProviderDeclineCode()).isEqualTo("insufficient_funds");
        assertThat(restored.getFailure()).isEqualTo(payment.getFailure());
    }

    @Test
    void mapsAllFieldsFromDomainToEntity() {
        Payment payment = aPayment()
                .withProvider(PaymentProvider.PAYPAL)
                .withStatus(PaymentStatus.AUTHORIZED)
                .buildRestored();

        PaymentEntity entity = PaymentEntity.fromDomain(payment);

        assertThat(entity)
                .extracting(
                        PaymentEntity::getId,
                        PaymentEntity::getSourceAccountId,
                        PaymentEntity::getDestinationAccountId,
                        PaymentEntity::getReferenceId,
                        PaymentEntity::getAmount,
                        PaymentEntity::getCurrency,
                        PaymentEntity::getStatus,
                        PaymentEntity::getPaymentProvider,
                        PaymentEntity::getCreatedAt,
                        PaymentEntity::getUpdatedAt
                )
                .containsExactly(
                        payment.getId().value(),
                        payment.getSourceAccountId().value(),
                        payment.getDestinationAccountId().value(),
                        payment.getReferenceId(),
                        payment.getAmount().amount(),
                        payment.getAmount().currency().getCurrencyCode(),
                        payment.getStatus().name(),
                        payment.getPaymentProvider().name(),
                        payment.getCreatedAt(),
                        payment.getUpdatedAt()
                );
    }

    @Test
    void mapsAllFieldsFromEntityToDomainWithoutCreatingEvents() {
        Payment expectedPayment = aPayment()
                .withProvider(PaymentProvider.PAYPAL)
                .withStatus(PaymentStatus.AUTHORIZED)
                .buildRestored();

        PaymentEntity entity = PaymentEntity.builder()
                .id(expectedPayment.getId().value())
                .sourceAccountId(expectedPayment.getSourceAccountId().value())
                .destinationAccountId(expectedPayment.getDestinationAccountId().value())
                .referenceId(expectedPayment.getReferenceId())
                .amount(expectedPayment.getAmount().amount())
                .currency(expectedPayment.getAmount().currency().getCurrencyCode())
                .status(expectedPayment.getStatus().name())
                .paymentProvider(expectedPayment.getPaymentProvider().name())
                .createdAt(expectedPayment.getCreatedAt())
                .updatedAt(expectedPayment.getUpdatedAt())
                .build();

        Payment payment = entity.toDomain();

        assertThat(payment)
                .extracting(
                        Payment::getId,
                        Payment::getSourceAccountId,
                        Payment::getDestinationAccountId,
                        Payment::getReferenceId,
                        Payment::getAmount,
                        Payment::getPaymentProvider,
                        Payment::getStatus,
                        Payment::getCreatedAt,
                        Payment::getUpdatedAt
                )
                .containsExactly(
                        expectedPayment.getId(),
                        expectedPayment.getSourceAccountId(),
                        expectedPayment.getDestinationAccountId(),
                        expectedPayment.getReferenceId(),
                        expectedPayment.getAmount(),
                        expectedPayment.getPaymentProvider(),
                        expectedPayment.getStatus(),
                        expectedPayment.getCreatedAt(),
                        expectedPayment.getUpdatedAt()
                );
        assertThat(payment.getDomainEvents()).isEmpty();
    }
}
