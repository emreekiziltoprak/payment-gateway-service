package com.emrekiziltoprak.payment.gateway.service.adapters.out.gateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.ProviderPaymentReference;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.PaymentGatewayException;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ActionRequiredObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.AuthorizationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CaptureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.FailureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class StripePaymentAdapterTests {

    private static final String API_URL = "https://stripe.test/v1/payment_intents";
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T10:00:00Z");

    private StripePaymentAdapter adapter;
    private MockRestServiceServer server;
    private Payment payment;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        adapter = new StripePaymentAdapter(
                restTemplate,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(adapter, "stripeApiUrl", API_URL);
        payment = aPayment().withoutProviderReference().buildRestored();
    }

    @Test
    void mapsAutomaticCaptureToCaptureObservation() {
        respondWith("""
                {
                  "id": "pi_sync_123",
                  "status": "succeeded",
                  "amount": 2500,
                  "amount_received": 2500,
                  "currency": "try",
                  "capture_method": "automatic"
                }
                """);

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(CaptureObservation.class, observation -> {
            assertThat(observation.context().internalPaymentId()).contains(payment.getId());
            assertThat(observation.context().providerReference()).isEqualTo(
                    new ProviderPaymentReference(PaymentProvider.STRIPE, "pi_sync_123")
            );
            assertThat(observation.context().amount()).isEqualTo(payment.getAmount());
            assertThat(observation.context().observedAt()).isEqualTo(OBSERVED_AT);
            assertThat(observation.context().providerOccurredAt()).isEmpty();
        });
        server.verify();
    }

    @Test
    void mapsManualCapturePathToAuthorizationObservation() {
        respondWith("""
                {
                  "id": "pi_manual_123",
                  "status": "requires_capture",
                  "amount": 2500,
                  "amount_capturable": 2500,
                  "currency": "try",
                  "capture_method": "manual"
                }
                """);

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(AuthorizationObservation.class, observation -> {
            assertThat(observation.context().providerReference().value())
                    .isEqualTo("pi_manual_123");
            assertThat(observation.context().amount()).isEqualTo(payment.getAmount());
        });
        server.verify();
    }

    @Test
    void mapsSynchronousActionRequirement() {
        respondWith("""
                {
                  "id": "pi_action_123",
                  "status": "requires_action",
                  "amount": 2500,
                  "currency": "try",
                  "next_action": {"type": "use_stripe_sdk"}
                }
                """);

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(ActionRequiredObservation.class, observation -> {
            assertThat(observation.action().type()).isEqualTo("use_stripe_sdk");
            assertThat(observation.action().redirectUri()).isNull();
        });
        server.verify();
    }

    @Test
    void preservesSynchronousStripeDeclineCodes() {
        respondWith("""
                {
                  "id": "pi_failed_123",
                  "status": "requires_payment_method",
                  "amount": 2500,
                  "currency": "try",
                  "last_payment_error": {
                    "type": "card_error",
                    "code": "card_declined",
                    "decline_code": "insufficient_funds",
                    "message": "Your card has insufficient funds."
                  }
                }
                """);

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(FailureObservation.class, observation -> {
            assertThat(observation.failure().code()).isEqualTo(PaymentFailureCode.DECLINED);
            assertThat(observation.failure().providerCode()).isEqualTo("card_declined");
            assertThat(observation.failure().providerDeclineCode())
                    .isEqualTo("insufficient_funds");
        });
        server.verify();
    }

    @Test
    void mapsHttp402ErrorPayloadAndPreservesDeclineFacts() {
        server.expect(once(), requestTo(API_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "type": "card_error",
                                    "code": "card_declined",
                                    "decline_code": "insufficient_funds",
                                    "message": "Your card has insufficient funds.",
                                    "payment_intent": {
                                      "id": "pi_declined_402",
                                      "amount": 2500,
                                      "currency": "try",
                                      "status": "requires_payment_method"
                                    }
                                  }
                                }
                                """));

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(FailureObservation.class, observation -> {
            assertThat(observation.context().internalPaymentId()).contains(payment.getId());
            assertThat(observation.context().providerReference().value())
                    .isEqualTo("pi_declined_402");
            assertThat(observation.context().amount()).isEqualTo(payment.getAmount());
            assertThat(observation.failure().code()).isEqualTo(PaymentFailureCode.DECLINED);
            assertThat(observation.failure().providerCode()).isEqualTo("card_declined");
            assertThat(observation.failure().providerDeclineCode())
                    .isEqualTo("insufficient_funds");
        });
        server.verify();
    }

    @Test
    void rejectsMalformedHttp400InsteadOfTreatingItAsTimeout() {
        server.expect(once(), requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("not-json"));

        assertThatThrownBy(() -> adapter.processPayment(payment))
                .isExactlyInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("without lifecycle failure facts");
    }

    @Test
    void rejectsCaptureMissingActualCapturedAmount() {
        respondWith("""
                {
                  "id": "pi_malformed_123",
                  "status": "succeeded",
                  "amount": 2500,
                  "currency": "try"
                }
                """);

        assertThatThrownBy(() -> adapter.processPayment(payment))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("amount_received");
    }

    @Test
    void rejectsRequiresConfirmationInsteadOfCallingItProcessing() {
        respondWith("""
                {
                  "id": "pi_unconfirmed_123",
                  "status": "requires_confirmation",
                  "amount": 2500,
                  "currency": "try"
                }
                """);

        assertThatThrownBy(() -> adapter.processPayment(payment))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unsupported Stripe status");
    }

    @Test
    void sendsZeroDecimalCurrencyInStripeMinorUnits() {
        payment = aPayment()
                .withoutProviderReference()
                .withAmount(Money.of(new BigDecimal("500"), "JPY"))
                .buildRestored();
        server.expect(once(), requestTo(API_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amount\":500")))
                .andRespond(withSuccess("""
                        {
                          "id": "pi_jpy_123",
                          "status": "succeeded",
                          "amount": 500,
                          "amount_received": 500,
                          "currency": "jpy"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.processPayment(payment)).isInstanceOf(CaptureObservation.class);
        server.verify();
    }

    private void respondWith(String json) {
        server.expect(once(), requestTo(API_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
