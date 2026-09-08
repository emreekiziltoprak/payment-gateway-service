package com.emrekiziltoprak.payment.gateway.service.integration;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataIdempotencyRepository;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataOutboxRepository;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataPaymentRepository;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.OutboxEntity;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.PaymentEntity;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import com.stripe.Stripe;
import com.stripe.net.Webhook;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.servlet.function.RequestPredicates.contentType;

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
    private static final String WEBHOOK_SECRET = "whsec_test";
    private static final String PAYMENT_REFERENCE = "pi_test_123";

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

    @Test
    void manualAuthorizationShouldBecomeCapturedAfterSuccesfulWebhook() throws Exception {

        when(paymentGatewayPort.processPayment(any(Payment.class)))
                .thenAnswer(invocation -> {
                   Payment payment = invocation.getArgument(0);

                   return anObservation()
                           .withInternalPaymentId(payment.getId())
                           .withProviderReference(PAYMENT_REFERENCE)
                           .withAmount(payment.getAmount())
                           .withObservedAt(Instant.now())
                           .withoutProviderOccurredAt()
                           .authorization();
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

        mockMvc.perform(
                post("/api/v1/payments")
                        .header("Idempotency-Key", "sync-manual-capture-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        //newly added payment
        PaymentEntity paymentEntity = paymentRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow();

        assertThat(paymentEntity.getStatus())
                .isEqualTo(PaymentStatus.AUTHORIZED.name());

        assertThat(paymentEntity.getReferenceId())
                .isEqualTo(PAYMENT_REFERENCE);

        //assert
        String webhookPayload= """
                {
                "id": "evt_123",
                "object": "event",
                "api_version": "%s",
                "created": %d,
                "type": "payment_intent.succeeded",
                "data": {
                    "object": {
                        "id": "%s",
                        "object": "payment_intent",
                        "amount": 2500,
                        "amount_received": 2500,
                        "currency": "TRY",
                        "status": "succeeded",
                        "capture_method": "manual",
                        "metadata": {
                        "payment_id": "%s"
                        }
                    }
                }
             }""".formatted(
                Stripe.API_VERSION,
                Instant.now().getEpochSecond(),
                PAYMENT_REFERENCE,
                paymentEntity.getId()
        );

        String signature =
                Webhook.Signature.generateSignatureHeader(
                 webhookPayload,
                 WEBHOOK_SECRET
                );

        mockMvc.perform(
                post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload)
        )
        .andExpect(status().isOk());

        //payment with new state not snapshot
        PaymentEntity captured = paymentRepository
                .findById(paymentEntity.getId())
                .orElseThrow();

        assertThat(captured.getStatus())
                .isEqualTo(PaymentStatus.CAPTURED.name());

        assertThat(outboxEventRepository.findAll())
                .extracting(OutboxEntity::getEventType)
                .containsExactlyInAnyOrder("PaymentInitiated","PaymentCaptured");

        //second capture
        mockMvc.perform(
                        post("/api/v1/webhooks/stripe")
                                .header("Stripe-Signature", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(webhookPayload)
                )
                .andExpect(status().isOk());


        //the second capture shouldn't create another outbox event
        assertThat(outboxEventRepository.findAll())
                .extracting(OutboxEntity::getEventType)
                .containsOnlyOnce("PaymentCaptured");




    }

    @Test
    void shouldNotChangeStateWhenStaleAuthorizationOccursAfterCapture() throws Exception {
        when(paymentGatewayPort.processPayment(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);

                    return anObservation()
                            .withInternalPaymentId(payment.getId())
                            .withProviderReference(PAYMENT_REFERENCE)
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

        mockMvc.perform(
                        post("/api/v1/payments")
                                .header("Idempotency-Key", "stale-authorization-after-capture-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk());

        //newly added payment
        PaymentEntity paymentEntity = paymentRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow();

        //state is now captured
        assertThat(paymentEntity.getStatus())
                .isEqualTo(PaymentStatus.CAPTURED.name());

        //assert
        String webhookPayload = """
        {
          "id": "evt_123",
          "object": "event",
          "api_version": "%s",
          "created": %d,
          "type": "payment_intent.amount_capturable_updated",
          "data": {
            "object": {
              "id": "%s",
              "object": "payment_intent",
              "amount": 2500,
              "amount_capturable": 2500,
              "amount_received": 0,
              "currency": "try",
              "status": "requires_capture",
              "capture_method": "manual",
              "metadata": {
                "payment_id": "%s"
              }
            }
          }
        }
        """.formatted(
                Stripe.API_VERSION,
                Instant.now().getEpochSecond(),
                PAYMENT_REFERENCE,
                paymentEntity.getId()
        );


        String signature =
                Webhook.Signature.generateSignatureHeader(
                        webhookPayload,
                        WEBHOOK_SECRET
                );

        mockMvc.perform(
                        post("/api/v1/webhooks/stripe")
                                .header("Stripe-Signature", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(webhookPayload)
                )
                .andExpect(status().isOk());

        assertThat(paymentRepository.findById(paymentEntity.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CAPTURED.name());



    }

}
