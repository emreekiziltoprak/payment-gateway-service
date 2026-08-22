package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites;

import com.emrekiziltoprak.payment.gateway.service.domain.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
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
                .amount(payment.getAmount().amount())
                .currency(payment.getAmount().currency().getCurrencyCode())
                .status(payment.getStatus().name())
                .paymentProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().name() : null)
                .build();
    }

    public Payment toDomain() {
        throw new UnsupportedOperationException("Cannot convert entity to domain - use aggregate reconstruction");
    }
}
