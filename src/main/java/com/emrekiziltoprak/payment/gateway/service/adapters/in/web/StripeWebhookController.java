package com.emrekiziltoprak.payment.gateway.service.adapters.in.web;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand.CallbackStatus;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@Slf4j
public class StripeWebhookController {

    private final ProcessPaymentCallbackUseCase callbackUseCase;
    private final String endpointSecret;

    public StripeWebhookController(
            ProcessPaymentCallbackUseCase callbackUseCase,
            @Value("${stripe.webhook.secret}") String endpointSecret) {
        this.callbackUseCase = callbackUseCase;
        if (endpointSecret == null || endpointSecret.isBlank()) {
            throw new IllegalArgumentException("stripe.webhook.secret cannot be blank");
        }
        this.endpointSecret = endpointSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader) {

        if (signatureHeader == null || signatureHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Event event = Webhook.constructEvent(payload, signatureHeader, endpointSecret);

            //unrelated events are bypassed as customer.created
            CallbackStatus status = mapToDomainStatus(event.getType());
            if (status == null) {
                return ResponseEntity.ok().build();
            }

            StripeObject stripeObject = event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new IllegalStateException(
                            "Unable to deserialize Stripe event " + event.getId()
                    ));

            if (!(stripeObject instanceof PaymentIntent paymentIntent)) {
                throw new IllegalStateException(
                        "Unexpected Stripe object for event " + event.getType()
                );
            }

            String failureReason = paymentIntent.getLastPaymentError() != null
                    ? paymentIntent.getLastPaymentError().getMessage()
                    : null;

            ProcessPaymentCallbackCommand command = new ProcessPaymentCallbackCommand(
                    PaymentProvider.STRIPE,
                    extractInternalPaymentId(paymentIntent),
                    paymentIntent.getId(),
                    status,
                    failureReason
            );

            callbackUseCase.processCallback(command);

            return ResponseEntity.ok().build();

        } catch (SignatureVerificationException e) {
            log.warn("Rejected Stripe webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private CallbackStatus mapToDomainStatus(String stripeEventType) {
        return switch (stripeEventType) {
            case "payment_intent.succeeded" -> CallbackStatus.SUCCESS;
            case "payment_intent.payment_failed" -> CallbackStatus.FAILED;
            case "payment_intent.requires_action" -> CallbackStatus.REQUIRES_ACTION;
            case "payment_intent.processing" -> CallbackStatus.PROCESSING;
            case "payment_intent.canceled" -> CallbackStatus.CANCELED;
            default -> null;
        };
    }

    private PaymentId extractInternalPaymentId(PaymentIntent paymentIntent) {
        Map<String, String> metadata = paymentIntent.getMetadata();
        String paymentId = metadata != null ? metadata.get("payment_id") : null;

        if (paymentId == null || paymentId.isBlank()) {
            return null;
        }

        try {
            return new PaymentId(UUID.fromString(paymentId));
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring invalid payment_id metadata on Stripe PaymentIntent {}",
                    paymentIntent.getId());
            return null;
        }
    }
}
