package com.emrekiziltoprak.payment.gateway.service.adapters.in.web;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import com.stripe.Stripe;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StripeWebhookControllerTests {

    private static final String ENDPOINT_SECRET = "whsec_test_controller";

    private ProcessPaymentCallbackUseCase callbackUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        callbackUseCase = mock(ProcessPaymentCallbackUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StripeWebhookController(callbackUseCase, ENDPOINT_SECRET))
                .build();
    }

    @Test
    void rejectsBlankSigningSecretAtStartup() {
        assertThatThrownBy(() -> new StripeWebhookController(callbackUseCase, " "))
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
    void mapsValidSucceededEventToCallbackUseCase() throws Exception {
        String payload = succeededEventPayload(Stripe.API_VERSION);
        String signature = Webhook.Signature.generateSignatureHeader(payload, ENDPOINT_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<ProcessPaymentCallbackCommand> commandCaptor =
                ArgumentCaptor.forClass(ProcessPaymentCallbackCommand.class);
        verify(callbackUseCase).processCallback(commandCaptor.capture());

        assertThat(commandCaptor.getValue().paymentReference()).isEqualTo("pi_test_123");
        assertThat(commandCaptor.getValue().paymentId()).isEqualTo(
                new PaymentId(UUID.fromString("00000000-0000-0000-0000-000000000123"))
        );
        assertThat(commandCaptor.getValue().provider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(commandCaptor.getValue().status())
                .isEqualTo(ProcessPaymentCallbackCommand.CallbackStatus.CAPTURED);
    }

    @Test
    void mapsStripeDeclineDetailsToDomainFailureWithoutLosingProviderCodes() throws Exception {
        String payload = failedEventPayload(
                "card_error",
                "card_declined",
                "insufficient_funds",
                "Your card has insufficient funds."
        );
        String signature = Webhook.Signature.generateSignatureHeader(payload, ENDPOINT_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<ProcessPaymentCallbackCommand> commandCaptor =
                ArgumentCaptor.forClass(ProcessPaymentCallbackCommand.class);
        verify(callbackUseCase).processCallback(commandCaptor.capture());

        assertThat(commandCaptor.getValue().status())
                .isEqualTo(ProcessPaymentCallbackCommand.CallbackStatus.FAILED);
        assertThat(commandCaptor.getValue().failure().code()).isEqualTo(PaymentFailureCode.DECLINED);
        assertThat(commandCaptor.getValue().failure().providerCode()).isEqualTo("card_declined");
        assertThat(commandCaptor.getValue().failure().providerDeclineCode()).isEqualTo("insufficient_funds");
        assertThat(commandCaptor.getValue().failure().detail())
                .isEqualTo("Your card has insufficient funds.");
    }

    @Test
    void classifiesStripeProviderTimeoutWithoutHardCodingItAsDeclined() throws Exception {
        String payload = failedEventPayload(
                "api_error",
                "payment_method_provider_timeout",
                null,
                "The payment method provider timed out."
        );
        String signature = Webhook.Signature.generateSignatureHeader(payload, ENDPOINT_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<ProcessPaymentCallbackCommand> commandCaptor =
                ArgumentCaptor.forClass(ProcessPaymentCallbackCommand.class);
        verify(callbackUseCase).processCallback(commandCaptor.capture());

        assertThat(commandCaptor.getValue().failure().code()).isEqualTo(PaymentFailureCode.TIMEOUT);
        assertThat(commandCaptor.getValue().failure().providerCode())
                .isEqualTo("payment_method_provider_timeout");
        assertThat(commandCaptor.getValue().failure().providerDeclineCode()).isNull();
    }

    @Test
    void returnsServerErrorWhenKnownEventCannotBeDeserialized() throws Exception {
        String payload = succeededEventPayload("2000-01-01");
        String signature = Webhook.Signature.generateSignatureHeader(payload, ENDPOINT_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isInternalServerError());

        verifyNoInteractions(callbackUseCase);
    }

    private String succeededEventPayload(String apiVersion) {
        return """
                {
                  "id": "evt_test_123",
                  "object": "event",
                  "api_version": "%s",
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "pi_test_123",
                      "object": "payment_intent",
                      "status": "succeeded",
                      "metadata": {
                        "payment_id": "00000000-0000-0000-0000-000000000123"
                      }
                    }
                  }
                }
                """.formatted(apiVersion);
    }

    private String failedEventPayload(
            String errorType,
            String errorCode,
            String declineCode,
            String message
    ) {
        String declineCodeJson = declineCode == null
                ? "null"
                : "\"" + declineCode + "\"";
        return """
                {
                  "id": "evt_test_failed_123",
                  "object": "event",
                  "api_version": "%s",
                  "type": "payment_intent.payment_failed",
                  "data": {
                    "object": {
                      "id": "pi_test_failed_123",
                      "object": "payment_intent",
                      "status": "requires_payment_method",
                      "metadata": {
                        "payment_id": "00000000-0000-0000-0000-000000000123"
                      },
                      "last_payment_error": {
                        "type": "%s",
                        "code": "%s",
                        "decline_code": %s,
                        "message": "%s"
                      }
                    }
                  }
                }
                """.formatted(Stripe.API_VERSION, errorType, errorCode, declineCodeJson, message);
    }
}
