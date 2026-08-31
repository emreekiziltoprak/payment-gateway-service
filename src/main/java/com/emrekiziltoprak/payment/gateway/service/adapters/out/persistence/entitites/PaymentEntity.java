package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentCancellationReason;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_provider_reference",
                columnNames = {"payment_provider", "reference_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason")
    private PaymentCancellationReason cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code")
    private PaymentFailureCode failureCode;

    @Column(name = "failure_detail")
    private String failureDetail;

    @Column(name = "provider_error_code")
    private String providerErrorCode;

    @Column(name = "provider_decline_code")
    private String providerDeclineCode;

    @Column(name = "payment_provider")
    private String paymentProvider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaymentEntity fromDomain(Payment payment) {
        PaymentEntityBuilder builder = PaymentEntity.builder()
                .id(payment.getId().value())
                .sourceAccountId(payment.getSourceAccountId().value())
                .destinationAccountId(payment.getDestinationAccountId().value())
                .referenceId(payment.getPaymentRef().value())
                .amount(payment.getAmount().amount())
                .currency(payment.getAmount().currency().getCurrencyCode())
                .status(payment.getStatus().name())
                .cancellationReason(payment.getCancellationReason())
                .paymentProvider(payment.getPaymentRef().provider().name())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt());

        PaymentFailure failure = payment.getFailure();
        if (failure != null) {
            builder
                    .failureCode(failure.code())
                    .failureDetail(failure.detail())
                    .providerErrorCode(failure.providerCode())
                    .providerDeclineCode(failure.providerDeclineCode());
        }

        return builder.build();
    }

    public Payment toDomain() {
        PaymentId paymentId = new PaymentId(this.id);
        AccountId sourceId = new AccountId(this.sourceAccountId);
        AccountId destId = new AccountId(this.destinationAccountId);

        Money money = new Money(this.amount, java.util.Currency.getInstance(this.currency));

        PaymentStatus paymentStatus = PaymentStatus.valueOf(this.status);
        PaymentProvider provider = this.paymentProvider != null ? PaymentProvider.valueOf(this.paymentProvider) : null;
        return Payment.restore(
                paymentId,
                sourceId,
                destId,
                this.referenceId,
                money,
                provider,
                paymentStatus,
                toDomainFailure(),
                this.cancellationReason,
                this.createdAt,
                this.updatedAt
        );
    }

    private PaymentFailure toDomainFailure() {
        if (failureCode == null) {
            return null;
        }

        return PaymentFailure.fromProvider(
                failureCode,
                providerErrorCode,
                providerDeclineCode,
                failureDetail
        );
    }
}
