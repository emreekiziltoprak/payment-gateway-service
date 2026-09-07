package com.emrekiziltoprak.payment.gateway.service.adapters.out.gateway;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.PaymentGatewayException;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CaptureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.FailureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ProcessingObservation;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class IyzicoPaymentAdapterTests {

    private static final String API_URL = "https://iyzico.test/payment/auth";
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final long SYSTEM_TIME = 1767261540000L;

    private IyzicoPaymentAdapter adapter;
    private MockRestServiceServer server;
    private Payment payment;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        adapter = new IyzicoPaymentAdapter(
                restTemplate,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(adapter, "iyzicoApiUrl", API_URL);
        payment = aPayment()
                .withProvider(PaymentProvider.IYZICO)
                .withoutProviderReference()
                .buildRestored();
    }

    @Test
    void mapsApprovedSuccessToCaptureObservationWithProviderFacts() {
        respondWith(successResponse(1));

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(CaptureObservation.class, observation -> {
            assertThat(observation.context().internalPaymentId()).contains(payment.getId());
            assertThat(observation.context().providerReference().provider())
                    .isEqualTo(PaymentProvider.IYZICO);
            assertThat(observation.context().providerReference().value())
                    .isEqualTo("iyzico-payment-123");
            assertThat(observation.context().amount()).isEqualTo(payment.getAmount());
            assertThat(observation.context().observedAt()).isEqualTo(OBSERVED_AT);
            assertThat(observation.context().providerOccurredAt())
                    .contains(Instant.ofEpochMilli(SYSTEM_TIME));
        });
        server.verify();
    }

    @Test
    void mapsFraudReviewToProcessingObservation() {
        respondWith(successResponse(0));

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOf(ProcessingObservation.class);
        server.verify();
    }

    @Test
    void mapsDirectFraudRejectionToStructuredDecline() {
        respondWith(successResponse(-1));

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(FailureObservation.class, observation -> {
            assertThat(observation.failure().code()).isEqualTo(PaymentFailureCode.DECLINED);
            assertThat(observation.failure().providerCode()).isEqualTo("fraud_status_-1");
        });
        server.verify();
    }

    @Test
    void mapsBankDeclineWithoutProviderReferenceAndPreservesCodes() {
        respondWith("""
                {
                  "status": "failure",
                  "errorCode": "10051",
                  "errorGroup": "NOT_SUFFICIENT_FUNDS",
                  "errorMessage": "Insufficient card limit, insufficient balance.",
                  "locale": "en",
                  "systemTime": %d,
                  "conversationId": "%s"
                }
                """.formatted(SYSTEM_TIME, payment.getId().value()));

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(FailureObservation.class, observation -> {
            assertThat(observation.context().providerReference().provider())
                    .isEqualTo(PaymentProvider.IYZICO);
            assertThat(observation.context().providerReference().hasValue()).isFalse();
            assertThat(observation.context().amount()).isEqualTo(payment.getAmount());
            assertThat(observation.failure().code()).isEqualTo(PaymentFailureCode.DECLINED);
            assertThat(observation.failure().providerCode()).isEqualTo("10051");
            assertThat(observation.failure().providerDeclineCode())
                    .isEqualTo("NOT_SUFFICIENT_FUNDS");
        });
        server.verify();
    }

    @Test
    void mapsTechnicalErrorToGatewayFailure() {
        respondWith("""
                {
                  "status": "failure",
                  "errorCode": "5000",
                  "errorGroup": "SYSTEM_ERROR",
                  "errorMessage": "Provider is temporarily unavailable.",
                  "systemTime": %d,
                  "conversationId": "%s"
                }
                """.formatted(SYSTEM_TIME, payment.getId().value()));

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(FailureObservation.class, observation -> {
            assertThat(observation.failure().code())
                    .isEqualTo(PaymentFailureCode.GATEWAY_ERROR);
            assertThat(observation.failure().providerCode()).isEqualTo("5000");
            assertThat(observation.failure().detail())
                    .isEqualTo("Provider is temporarily unavailable.");
        });
        server.verify();
    }

    @Test
    void mapsStructuredHttp400FailureInsteadOfTreatingItAsTimeout() {
        server.expect(once(), requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "status": "failure",
                                  "errorCode": "10051",
                                  "errorGroup": "NOT_SUFFICIENT_FUNDS",
                                  "errorMessage": "Insufficient balance.",
                                  "systemTime": %d,
                                  "conversationId": "%s"
                                }
                                """.formatted(SYSTEM_TIME, payment.getId().value())));

        PaymentLifecycleObservation result = adapter.processPayment(payment);

        assertThat(result).isInstanceOfSatisfying(FailureObservation.class, observation -> {
            assertThat(observation.failure().code()).isEqualTo(PaymentFailureCode.DECLINED);
            assertThat(observation.failure().providerCode()).isEqualTo("10051");
            assertThat(observation.context().amount()).isEqualTo(payment.getAmount());
        });
        server.verify();
    }

    @Test
    void rejectsFailureWithOnlyPartOfProviderAmountFacts() {
        respondWith("""
                {
                  "status": "failure",
                  "errorCode": "5000",
                  "errorMessage": "Provider error",
                  "price": 25.00,
                  "conversationId": "%s"
                }
                """.formatted(payment.getId().value()));

        assertThatThrownBy(() -> adapter.processPayment(payment))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("incomplete amount facts");
    }

    @Test
    void rejectsFraudRejectionMissingSuccessMaterialFacts() {
        respondWith("""
                {
                  "status": "success",
                  "fraudStatus": -1,
                  "conversationId": "%s"
                }
                """.formatted(payment.getId().value()));

        assertThatThrownBy(() -> adapter.processPayment(payment))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("paymentId");
    }

    @Test
    void rejectsSuccessfulResponseWithoutPaymentReference() {
        respondWith("""
                {
                  "status": "success",
                  "fraudStatus": 1,
                  "price": 25.00,
                  "currency": "TRY",
                  "systemTime": %d,
                  "conversationId": "%s"
                }
                """.formatted(SYSTEM_TIME, payment.getId().value()));

        assertThatThrownBy(() -> adapter.processPayment(payment))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("paymentId");
    }

    @Test
    void rejectsUnknownFraudStatus() {
        respondWith(successResponse(7));

        assertThatThrownBy(() -> adapter.processPayment(payment))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("fraudStatus");
    }

    private String successResponse(int fraudStatus) {
        return """
                {
                  "status": "success",
                  "paymentId": "iyzico-payment-123",
                  "fraudStatus": %d,
                  "price": 25.00,
                  "paidPrice": 26.00,
                  "currency": "TRY",
                  "systemTime": %d,
                  "conversationId": "%s"
                }
                """.formatted(fraudStatus, SYSTEM_TIME, payment.getId().value());
    }

    private void respondWith(String json) {
        server.expect(once(), requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
