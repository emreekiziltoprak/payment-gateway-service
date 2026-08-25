package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites;

import com.emrekiziltoprak.payment.gateway.service.domain.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

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

    @Column(name = "payment_provider")
    private String paymentProvider;

    public static PaymentEntity fromDomain(Payment payment) {
        return PaymentEntity.builder()
                .id(payment.getId().value())
                .sourceAccountId(payment.getSourceAccountId().value())
                .destinationAccountId(payment.getDestinationAccountId().value())
                .referenceId(payment.getReferenceId())
                .amount(payment.getAmount().amount())
                .currency(payment.getAmount().currency().getCurrencyCode())
                .status(payment.getStatus().name())
                .paymentProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().name() : null)
                .build();
    }

    public Payment toDomain() {
        PaymentId paymentId = new PaymentId(this.id);
        AccountId sourceId = new AccountId(this.sourceAccountId);
        AccountId destId = new AccountId(this.destinationAccountId);

        Money money = new Money(this.amount, java.util.Currency.getInstance(this.currency));

        PaymentStatus paymentStatus = PaymentStatus.valueOf(this.status);
        PaymentProvider provider = this.paymentProvider != null ? PaymentProvider.valueOf(this.paymentProvider) : null;

        return new Payment(
                paymentId,
                sourceId,
                destId,
                this.referenceId,
                money,
                provider,
                paymentStatus
        );
    }


}
