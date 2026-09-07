package com.emrekiziltoprak.payment.gateway.service.adapters.in.web;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.emrekiziltoprak.payment.gateway.service.adapters.stripe.StripeLifecycleMapping;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.ProviderPaymentReference;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.PaymentGatewayException;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ActionRequiredObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.AuthorizationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CancellationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CaptureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.FailureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.LifecycleObservationContext;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ProcessingObservation;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.model.StripeObject;

@Component
public class StripeWebhookObservationMapper {

    private final Clock clock;

    public StripeWebhookObservationMapper() {
        this(Clock.systemUTC());
    }

    StripeWebhookObservationMapper(Clock clock) {
        this.clock = clock;
    }

    // Maps a supported Stripe webhook event to a domain lifecycle observation.
    public Optional<PaymentLifecycleObservation> map(Event event) {
        if (!isSupported(event.getType())) {
            return Optional.empty();
        }

        PaymentIntent paymentIntent = paymentIntent(event);
        Optional<PaymentId> internalPaymentId = extractInternalPaymentId(paymentIntent);
        Optional<Instant> providerOccurredAt = Optional.ofNullable(event.getCreated())
                .map(Instant::ofEpochSecond);

        PaymentLifecycleObservation observation = switch (event.getType()) {
            case "payment_intent.processing" -> new ProcessingObservation(
                    context(paymentIntent, paymentIntent.getAmount(), "amount",
                            internalPaymentId, providerOccurredAt)
            );
            case "payment_intent.requires_action" -> new ActionRequiredObservation(
                    context(paymentIntent, paymentIntent.getAmount(), "amount",
                            internalPaymentId, providerOccurredAt),
                    StripeLifecycleMapping.requiredAction(
                            paymentIntent.getNextAction() == null
                                    ? null
                                    : paymentIntent.getNextAction().getType(),
                            redirectUrl(paymentIntent)
                    )
            );
            case "payment_intent.amount_capturable_updated" -> new AuthorizationObservation(
                    context(paymentIntent, paymentIntent.getAmountCapturable(), "amount_capturable",
                            internalPaymentId, providerOccurredAt)
            );
            case "payment_intent.succeeded" -> new CaptureObservation(
                    context(paymentIntent, paymentIntent.getAmountReceived(), "amount_received",
                            internalPaymentId, providerOccurredAt)
            );
            case "payment_intent.payment_failed" -> new FailureObservation(
                    context(paymentIntent, paymentIntent.getAmount(), "amount",
                            internalPaymentId, providerOccurredAt),
                    failure(paymentIntent.getLastPaymentError())
            );
            case "payment_intent.canceled" -> new CancellationObservation(
                    context(paymentIntent, paymentIntent.getAmount(), "amount",
                            internalPaymentId, providerOccurredAt),
                    StripeLifecycleMapping.cancellation(paymentIntent.getCancellationReason())
            );
            default -> throw new IllegalStateException("Supported Stripe event was not mapped");
        };

        return Optional.of(observation);
    }

    // Builds the common lifecycle context from Stripe PaymentIntent data.
    private LifecycleObservationContext context(
            PaymentIntent paymentIntent,
            Long amountInMinorUnits,
            String materialFact,
            Optional<PaymentId> internalPaymentId,
            Optional<Instant> providerOccurredAt
    ) {
        if (paymentIntent.getId() == null || paymentIntent.getId().isBlank()) {
            throw new PaymentGatewayException(
                    "Stripe PaymentIntent is missing its provider reference"
            );
        }

        Money amount = StripeLifecycleMapping.moneyFromMinorUnits(
                amountInMinorUnits,
                paymentIntent.getCurrency(),
                materialFact
        );

        return new LifecycleObservationContext(
                internalPaymentId,
                new ProviderPaymentReference(
                        PaymentProvider.STRIPE,
                        paymentIntent.getId()
                ),
                amount,
                clock.instant(),
                providerOccurredAt
        );
    }

    // Extracts and validates the PaymentIntent object from the Stripe event.
    private PaymentIntent paymentIntent(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new PaymentGatewayException(
                        "Unable to deserialize Stripe event " + event.getId()
                ));

        if (stripeObject instanceof PaymentIntent paymentIntent) {
            return paymentIntent;
        }

        throw new PaymentGatewayException(
                "Unexpected Stripe object for event " + event.getType()
        );
    }

    // Extracts the internal payment ID from Stripe PaymentIntent metadata.
    private Optional<PaymentId> extractInternalPaymentId(
            PaymentIntent paymentIntent
    ) {
        Map<String, String> metadata = paymentIntent.getMetadata();
        String paymentId = metadata == null ? null : metadata.get("payment_id");

        if (paymentId == null || paymentId.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new PaymentId(UUID.fromString(paymentId)));
        } catch (IllegalArgumentException exception) {
            throw new PaymentGatewayException(
                    "Stripe metadata payment_id is not a valid UUID"
            );
        }
    }

    // Extracts the redirect URL required for the next Stripe payment action.
    private String redirectUrl(PaymentIntent paymentIntent) {
        if (paymentIntent.getNextAction() == null
                || paymentIntent.getNextAction().getRedirectToUrl() == null) {
            return null;
        }

        return paymentIntent
                .getNextAction()
                .getRedirectToUrl()
                .getUrl();
    }

    // Maps a Stripe payment error to the domain PaymentFailure model.
    private com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure failure(
            StripeError stripeError
    ) {
        if (stripeError == null) {
            return StripeLifecycleMapping.failure(null, null, null, null);
        }

        return StripeLifecycleMapping.failure(
                stripeError.getType(),
                stripeError.getCode(),
                stripeError.getDeclineCode(),
                stripeError.getMessage()
        );
    }

    // Checks whether the Stripe event type is supported by this mapper.
    private boolean isSupported(String eventType) {
        return switch (eventType) {
            case "payment_intent.processing",
                 "payment_intent.requires_action",
                 "payment_intent.amount_capturable_updated",
                 "payment_intent.succeeded",
                 "payment_intent.payment_failed",
                 "payment_intent.canceled" -> true;

            default -> false;
        };
    }
}
