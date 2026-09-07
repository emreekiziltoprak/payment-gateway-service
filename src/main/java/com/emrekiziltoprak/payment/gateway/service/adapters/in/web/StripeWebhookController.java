package com.emrekiziltoprak.payment.gateway.service.adapters.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/webhooks")
@Slf4j
public class StripeWebhookController {

    private final ProcessPaymentCallbackUseCase callbackUseCase;
    private final StripeWebhookObservationMapper observationMapper;
    private final String endpointSecret;

    public StripeWebhookController(
            ProcessPaymentCallbackUseCase callbackUseCase,
            StripeWebhookObservationMapper observationMapper,
            @Value("${stripe.webhook.secret}") String endpointSecret
    ) {
        this.callbackUseCase = callbackUseCase;
        this.observationMapper = observationMapper;
        if (endpointSecret == null || endpointSecret.isBlank()) {
            throw new IllegalArgumentException("stripe.webhook.secret cannot be blank");
        }
        this.endpointSecret = endpointSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader
    ) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Event event = Webhook.constructEvent(payload, signatureHeader, endpointSecret);
            PaymentLifecycleObservation observation = observationMapper.map(event).orElse(null);
            if (observation != null) {
                callbackUseCase.processCallback(observation);
            }
            return ResponseEntity.ok().build();
        } catch (SignatureVerificationException exception) {
            log.warn("Rejected Stripe webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception exception) {
            log.error("Failed to process Stripe webhook", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
