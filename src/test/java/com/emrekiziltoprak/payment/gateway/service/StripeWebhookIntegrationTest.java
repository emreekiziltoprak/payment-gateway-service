package com.emrekiziltoprak.payment.gateway.service;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataOutboxRepository;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataPaymentRepository;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.OutboxEntity;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.PaymentEntity;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import com.stripe.Stripe;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test covering the full webhook callback flow:
 * Controller → Service → Repository → Outbox
 * <p>
 * This test uses H2 in-memory database and embedded Kafka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "stripe.webhook.secret=whsec_test_integration"
})
class StripeWebhookIntegrationTest {

    private static final String ENDPOINT_SECRET = "whsec_test_integration";
    private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final String PAYMENT_REFERENCE = "pi_test_integration_123";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SpringDataPaymentRepository paymentRepository;

    @Autowired
    private SpringDataOutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        // Setup MockMvc
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // Clean database before each test
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();

        // Create a PROCESSING payment that will receive the callback
        Payment payment = aPayment()
                .withPaymentId(new PaymentId(PAYMENT_ID))
                .withProviderReference(PAYMENT_REFERENCE)
                .buildRestored();

        paymentRepository.save(PaymentEntity.fromDomain(payment));
    }

    @Test
    void shouldProcessSuccessfulWebhookAndCreateOutboxEvent() throws Exception {
        String payload = succeededEventPayload(Stripe.API_VERSION);

        String signature = Webhook.Signature.generateSignatureHeader(payload, ENDPOINT_SECRET);
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        PaymentEntity updatedPayment = paymentRepository.findById(PAYMENT_ID).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.CAPTURED.name());

        List<OutboxEntity> outboxEvents = outboxRepository.findAll();

        assertThat(outboxEvents).hasSize(1);

        OutboxEntity outboxEvent = outboxEvents.get(0);

        assertThat(outboxEvent.getEventType()).isEqualTo("PaymentCaptured");
        assertThat(outboxEvent.getPayload()).contains(PAYMENT_ID.toString());

        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    void shouldNotCreateDuplicateOutboxEventForRepeatedWebhook() throws Exception {
        String payload = succeededEventPayload(Stripe.API_VERSION);
        String signature = Webhook.Signature.generateSignatureHeader(payload, ENDPOINT_SECRET);

        // When: Same webhook is received twice
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        List<OutboxEntity> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
    }

    @Test
    void shouldRejectWebhookWithInvalidSignature() throws Exception {
        String payload = succeededEventPayload(Stripe.API_VERSION);
        String invalidSignature = "t=1,v1=invalid_signature";

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", invalidSignature)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        PaymentEntity payment = paymentRepository.findById(PAYMENT_ID).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING.name());

        // And: No outbox event should be created
        List<OutboxEntity> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).isEmpty();
    }

    private String succeededEventPayload(String apiVersion) {
        return """
                {
                  "id": "evt_test_integration_123",
                  "object": "event",
                  "api_version": "%s",
                  "created": 1767262140,
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "amount": 2500,
                      "amount_received": 2500,
                      "currency": "try",
                      "status": "succeeded",
                      "metadata": {
                        "payment_id": "%s"
                      }
                    }
                  }
                }
                """.formatted(apiVersion, PAYMENT_REFERENCE, PAYMENT_ID.toString());
    }
}
