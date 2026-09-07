package com.emrekiziltoprak.payment.gateway.service.adapters.in.web;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentCancellationReason;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.ProviderPaymentReference;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ActionRequiredObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.AuthorizationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CancellationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CaptureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.FailureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ProcessingObservation;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import com.stripe.Stripe;
import com.stripe.net.Webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StripeWebhookControllerTests {

    private static final String ENDPOINT_SECRET = "whsec_test_controller";
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final long PROVIDER_OCCURRED_AT_EPOCH = 1767261540L;
    private static final Instant PROVIDER_OCCURRED_AT =
            Instant.ofEpochSecond(PROVIDER_OCCURRED_AT_EPOCH);
    private static final PaymentId PAYMENT_ID = new PaymentId(
            UUID.fromString("00000000-0000-0000-0000-000000000123")
    );

    private ProcessPaymentCallbackUseCase callbackUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        callbackUseCase = mock(ProcessPaymentCallbackUseCase.class);
        StripeWebhookObservationMapper mapper = new StripeWebhookObservationMapper(
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new StripeWebhookController(callbackUseCase, mapper, ENDPOINT_SECRET)
                )
                .build();
    }

    @Test
    void rejectsBlankSigningSecretAtStartup() {
        StripeWebhookObservationMapper mapper = new StripeWebhookObservationMapper(
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> new StripeWebhookController(callbackUseCase, mapper, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");
    }

    @Test
    void rejectsRequestWithoutSignature() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(callbackUseCase);
    }

    @Test
    void rejectsRequestWithInvalidSignature() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=invalid")
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(callbackUseCase);
    }

    @Test
    void mapsProcessingEventToProcessingObservation() throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.processing",
                "processing",
                2500,
                "try",
                ""
        );

        postSigned(payload).andExpect(status().isOk());

        assertThat(capturedObservation()).isInstanceOf(ProcessingObservation.class);
    }

    @Test
    void mapsRedirectActionEventToActionRequiredObservation() throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.requires_action",
                "requires_action",
                2500,
                "try",
                """
                        ,"next_action": {
                          "type": "redirect_to_url",
                          "redirect_to_url": {"url": "https://payments.example/authorize"}
                        }
                        """
        );

        postSigned(payload).andExpect(status().isOk());

        assertThat(capturedObservation())
                .isInstanceOfSatisfying(ActionRequiredObservation.class, observation -> {
                    assertThat(observation.action().type()).isEqualTo("redirect_to_url");
                    assertThat(observation.action().redirectUri())
                            .isEqualTo(URI.create("https://payments.example/authorize"));
                });
    }

    @Test
    void mapsSdkActionWithoutInventingRedirectUri() throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.requires_action",
                "requires_action",
                2500,
                "try",
                """
                        ,"next_action": {"type": "use_stripe_sdk"}
                        """
        );

        postSigned(payload).andExpect(status().isOk());

        assertThat(capturedObservation())
                .isInstanceOfSatisfying(ActionRequiredObservation.class, observation -> {
                    assertThat(observation.action().type()).isEqualTo("use_stripe_sdk");
                    assertThat(observation.action().redirectUri()).isNull();
                });
    }

    @Test
    void mapsCapturableAmountToAuthorizationObservation() throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.amount_capturable_updated",
                "requires_capture",
                2500,
                "try",
                ",\"amount_capturable\": 1250"
        );

        postSigned(payload).andExpect(status().isOk());

        assertThat(capturedObservation())
                .isInstanceOfSatisfying(AuthorizationObservation.class, observation ->
                        assertThat(observation.context().amount())
                                .isEqualTo(Money.of(new BigDecimal("12.50"), "TRY"))
                );
    }

    @Test
    void mapsReceivedAmountAndMaterialFactsToCaptureObservation() throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.succeeded",
                "succeeded",
                2500,
                "try",
                ",\"amount_received\": 2000"
        );

        postSigned(payload).andExpect(status().isOk());

        assertThat(capturedObservation())
                .isInstanceOfSatisfying(CaptureObservation.class, observation -> {
                    assertThat(observation.context().internalPaymentId()).contains(PAYMENT_ID);
                    assertThat(observation.context().providerReference()).isEqualTo(
                            new ProviderPaymentReference(PaymentProvider.STRIPE, "pi_test_123")
                    );
                    assertThat(observation.context().amount())
                            .isEqualTo(Money.of(new BigDecimal("20.00"), "TRY"));
                    assertThat(observation.context().observedAt()).isEqualTo(OBSERVED_AT);
                    assertThat(observation.context().providerOccurredAt())
                            .contains(PROVIDER_OCCURRED_AT);
                });
    }

    @ParameterizedTest
    @CsvSource({
            "500, jpy, 500",
            "500, mga, 500.00",
            "500, isk, 5",
            "500, ugx, 5",
            "500, bhd, 0.500"
    })
    void convertsStripeMinorUnitsUsingStripeCurrencyRules(
            long minorUnits,
            String currency,
            String expectedAmount
    ) throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.succeeded",
                "succeeded",
                minorUnits,
                currency,
                ",\"amount_received\": " + minorUnits
        );

        postSigned(payload).andExpect(status().isOk());

        assertThat(capturedObservation())
                .isInstanceOfSatisfying(CaptureObservation.class, observation ->
                        assertThat(observation.context().amount()).isEqualTo(
                                Money.of(
                                        new BigDecimal(expectedAmount),
                                        Currency.getInstance(currency.toUpperCase(Locale.ROOT))
                                )
                        )
                );
    }

    @Test
    void preservesStripeDeclineCodesInFailureObservation() throws Exception {
        String payload = failedEvent(
                "card_error",
                "card_declined",
                "insufficient_funds",
                "Your card has insufficient funds."
        );

        postSigned(payload).andExpect(status().isOk());

        assertThat(capturedObservation())
                .isInstanceOfSatisfying(FailureObservation.class, observation -> {
                    assertThat(observation.failure().code()).isEqualTo(PaymentFailureCode.DECLINED);
                    assertThat(observation.failure().providerCode()).isEqualTo("card_declined");
                    assertThat(observation.failure().providerDeclineCode())
                            .isEqualTo("insufficient_funds");
                    assertThat(observation.failure().detail())
                            .isEqualTo("Your card has insufficient funds.");
                });
    }

    @Test
    void classifiesStripeProviderTimeout() throws Exception {
        String payload = failedEvent(
                "api_error",
                "payment_method_provider_timeout",
                null,
                "The payment method provider timed out."
        );

        postSigned(payload).andExpect(status().isOk());

        assertThat(capturedObservation())
                .isInstanceOfSatisfying(FailureObservation.class, observation -> {
                    assertThat(observation.failure().code()).isEqualTo(PaymentFailureCode.TIMEOUT);
                    assertThat(observation.failure().providerCode())
                            .isEqualTo("payment_method_provider_timeout");
                });
    }

    @ParameterizedTest
    @CsvSource({
            "requested_by_customer, CUSTOMER_REQUESTED",
            "abandoned, CUSTOMER_REQUESTED",
            "expired, AUTHORIZATION_EXPIRED",
            "automatic, PROVIDER_CANCELLED"
    })
    void mapsCancellationToCancellationSemantics(
            String providerReason,
            PaymentCancellationReason expectedReason
    ) throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.canceled",
                "canceled",
                2500,
                "try",
                ",\"cancellation_reason\": \"" + providerReason + "\""
        );

        postSigned(payload).andExpect(status().isOk());

        assertThat(capturedObservation())
                .isInstanceOfSatisfying(CancellationObservation.class, observation -> {
                    assertThat(observation.cancellation().reason()).isEqualTo(expectedReason);
                    assertThat(observation.cancellation().providerReason())
                            .isEqualTo(providerReason);
                });
    }

    @Test
    void acknowledgesUnknownEventWithoutLifecycleMutation() throws Exception {
        String payload = """
                {
                  "id": "evt_unknown_123",
                  "object": "event",
                  "api_version": "%s",
                  "created": %d,
                  "type": "customer.created",
                  "data": {"object": {"id": "cus_123", "object": "customer"}}
                }
                """.formatted(Stripe.API_VERSION, PROVIDER_OCCURRED_AT_EPOCH);

        postSigned(payload).andExpect(status().isOk());

        verifyNoInteractions(callbackUseCase);
    }

    @Test
    void rejectsCaptureWithoutReceivedAmountAtAdapterBoundary() throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.succeeded",
                "succeeded",
                2500,
                "try",
                ""
        );

        postSigned(payload).andExpect(status().isInternalServerError());

        verifyNoInteractions(callbackUseCase);
    }

    @Test
    void rejectsKnownEventWithoutCurrencyAtAdapterBoundary() throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.processing",
                "processing",
                2500,
                null,
                ""
        );

        postSigned(payload).andExpect(status().isInternalServerError());

        verifyNoInteractions(callbackUseCase);
    }

    @Test
    void rejectsMalformedInternalPaymentIdAtAdapterBoundary() throws Exception {
        String payload = paymentIntentEvent(
                "payment_intent.processing",
                "processing",
                2500,
                "try",
                ""
        ).replace(PAYMENT_ID.value().toString(), "not-a-payment-uuid");

        postSigned(payload).andExpect(status().isInternalServerError());

        verifyNoInteractions(callbackUseCase);
    }

    private PaymentLifecycleObservation capturedObservation() {
        ArgumentCaptor<PaymentLifecycleObservation> captor =
                ArgumentCaptor.forClass(PaymentLifecycleObservation.class);
        verify(callbackUseCase).processCallback(captor.capture());
        return captor.getValue();
    }

    private ResultActions postSigned(String payload) throws Exception {
        String signature = Webhook.Signature.generateSignatureHeader(payload, ENDPOINT_SECRET);
        return mockMvc.perform(post("/api/v1/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload));
    }

    private String paymentIntentEvent(
            String eventType,
            String paymentIntentStatus,
            long amount,
            String currency,
            String additionalFields
    ) {
        String currencyField = currency == null
                ? ""
                : ",\"currency\": \"" + currency + "\"";
        return """
                {
                  "id": "evt_test_123",
                  "object": "event",
                  "api_version": "%s",
                  "created": %d,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "pi_test_123",
                      "object": "payment_intent",
                      "amount": %d%s,
                      "status": "%s",
                      "metadata": {
                        "payment_id": "%s"
                      }
                      %s
                    }
                  }
                }
                """.formatted(
                Stripe.API_VERSION,
                PROVIDER_OCCURRED_AT_EPOCH,
                eventType,
                amount,
                currencyField,
                paymentIntentStatus,
                PAYMENT_ID.value(),
                additionalFields
        );
    }

    private String failedEvent(
            String errorType,
            String errorCode,
            String declineCode,
            String message
    ) {
        String declineCodeJson = declineCode == null
                ? "null"
                : "\"" + declineCode + "\"";
        return paymentIntentEvent(
                "payment_intent.payment_failed",
                "requires_payment_method",
                2500,
                "try",
                """
                        ,"last_payment_error": {
                          "type": "%s",
                          "code": "%s",
                          "decline_code": %s,
                          "message": "%s"
                        }
                        """.formatted(errorType, errorCode, declineCodeJson, message)
        );
    }
}
