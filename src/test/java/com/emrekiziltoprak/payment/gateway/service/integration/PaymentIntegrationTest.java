package com.emrekiziltoprak.payment.gateway.service.integration;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataIdempotencyRepository;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataOutboxRepository;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataPaymentRepository;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.OutboxEntity;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.PaymentEntity;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentLifecycleObservationTestFixture.anObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emrekiziltoprak.payment.gateway.service.testsupport.ProcessPaymentCommandTestFixture.*;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

@SpringBootTest(properties = {
  "stripe.webhook.secret=whsec_test",
  "spring.flyway.enabled=false",
  "outbox.poller.fixed-delay-ms=600000"
})

@AutoConfigureMockMvc
class PaymentIntegrationTest {

    @MockitoBean(name = "stripePaymentAdapter")
    private PaymentGatewayPort paymentGatewayPort;

    @Autowired
    private SpringDataPaymentRepository paymentRepository;

    @Autowired
    private SpringDataIdempotencyRepository idempotencyRepository;

    @Autowired
    private SpringDataOutboxRepository outboxEventRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        idempotencyRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void automaticCapturePersistsPaymentAndOutboxEvents() throws Exception {


        //arrange: define mock behavior
        //return capture on payment processing
        when(paymentGatewayPort.processPayment(any(Payment.class)))
                .thenAnswer(invocation -> {

                    Payment payment = invocation.getArgument(0);
                    //build a capture observation with context
                    return anObservation()
                            .withInternalPaymentId(payment.getId())
                            .withProviderReference("pi_123")
                            .withAmount(payment.getAmount())
                            .withObservedAt(Instant.now())
                            .withoutProviderOccurredAt()
                            .capture();

                });


        String requestBody = """
            {
              "sourceAccountId": "00000000-0000-0000-0000-000000000101",
              "destinationAccountId": "00000000-0000-0000-0000-000000000102",
              "amount": 25.00,
              "currency": "TRY",
              "provider": "STRIPE"
            }
            """;

        //act: create payment
        mockMvc.perform(
                post("/api/v1/payments")
                    .header("Idempotency-Key",
                            "sync-auto-capture-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)

                ).andExpect(status().isOk());

        //assert
        PaymentEntity savedEntity = paymentRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow();

        assertThat(savedEntity.getStatus())
                .isEqualTo(PaymentStatus.CAPTURED.name());

        assertThat(savedEntity.getReferenceId())
                .isEqualTo("pi_123");

        List<OutboxEntity> savedOutboxEvents = outboxEventRepository.findAll();

        assertThat(savedOutboxEvents)
                .extracting(OutboxEntity::getEventType)
                .containsExactlyInAnyOrder("PaymentCaptured", "PaymentInitiated");

        assertThat(savedOutboxEvents)
                .filteredOn(event -> event.getEventType().equals("PaymentCaptured"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getPayload())
                            .contains(savedEntity.getId().toString());
                    //event shouldn't be processed at that time !
                    //not sent kafka yet
                    assertThat(event.getProcessedAt()).isNull();
                });

        //verify: verify that payment gateway port was called once
        verify(paymentGatewayPort, times(1))
                .processPayment(any(Payment.class));

    }

}