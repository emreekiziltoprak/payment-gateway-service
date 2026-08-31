package com.emrekiziltoprak.payment.gateway.service.adapters.in.web;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand.CallbackStatus;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;

import lombok.extern.slf4j.Slf4j;

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

            PaymentFailure failure = mapFailure(status, paymentIntent.getLastPaymentError());

            ProcessPaymentCallbackCommand command = new ProcessPaymentCallbackCommand(
                    PaymentProvider.STRIPE,
                    extractInternalPaymentId(paymentIntent),
                    paymentIntent.getId(),
                    status,
                    failure
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

    //payment_intent.created --> is bypassing
    private CallbackStatus mapToDomainStatus(String stripeEventType) {
    return switch (stripeEventType) {
        // Para kesin olarak çekildi (Doğrudan ödeme veya manuel capture sonrası)
        case "payment_intent.succeeded" -> CallbackStatus.CAPTURED;

        // Para kartta bloke edildi, capture edilmeyi bekliyor
        case "payment_intent.amount_capturable_updated" -> CallbackStatus.CAPTURABLE;

        // Ödeme başarısız oldu (Bakiye yetersiz, kart reddedildi vb.)
        case "payment_intent.payment_failed" -> CallbackStatus.FAILED;

        // 3D Secure veya ek kullanıcı onayı gerekiyor
        case "payment_intent.requires_action" -> CallbackStatus.REQUIRES_ACTION;

        // Ödeme sağlayıcı tarafından işleniyor (Beklemede)
        case "payment_intent.processing" -> CallbackStatus.PROCESSING;

        // İşlem iptal edildi veya 7 günlük bloke süresi dolup düştü
        case "payment_intent.canceled" -> CallbackStatus.CANCELED;

        // Sisteminizin dinlemediği diğer Stripe eventleri için güvenli liman
        default -> null;
    };
}


    private PaymentFailure mapFailure(CallbackStatus status, StripeError stripeError) {
        if (status == CallbackStatus.CANCELED) {
            return PaymentFailure.of(PaymentFailureCode.CANCELLED, "Canceled by customer");
        }
        if (status != CallbackStatus.FAILED) {
            return null;
        }
        if (stripeError == null) {
            return PaymentFailure.of(
                    PaymentFailureCode.GATEWAY_ERROR,
                    "Stripe reported payment_intent.payment_failed without last_payment_error"
            );
        }

        return PaymentFailure.fromProvider(
                classifyStripeError(stripeError),
                stripeError.getCode(),
                stripeError.getDeclineCode(),
                stripeError.getMessage()
        );
    }

    private PaymentFailureCode classifyStripeError(StripeError stripeError) {
        String type = stripeError.getType();
        String code = stripeError.getCode();

        if ("payment_method_provider_timeout".equals(code)) {
            return PaymentFailureCode.TIMEOUT;
        }
        if ("invalid_request_error".equals(type)) {
            return PaymentFailureCode.VALIDATION_ERROR;
        }
        if (stripeError.getDeclineCode() != null
                || "card_error".equals(type)
                || "card_declined".equals(code)
                || "payment_method_provider_decline".equals(code)) {
            return PaymentFailureCode.DECLINED;
        }
        return PaymentFailureCode.GATEWAY_ERROR;
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
