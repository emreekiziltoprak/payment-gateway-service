package com.emrekiziltoprak.payment.gateway.service.adapters.stripe;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;

import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentCancellationReason;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.PaymentGatewayException;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentCancellation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.RequiredPaymentAction;

public final class StripeLifecycleMapping {

    // Stripe treats these currencies as zero-decimal currencies.
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
            "PYG", "RWF", "VND", "VUV", "XAF", "XOF", "XPF"
    );

    // Stripe expects these currencies to use two decimal places
    // even though their standard currency metadata differs.
    private static final Set<String> TWO_DECIMAL_STRIPE_SPECIAL_CASES = Set.of(
            "ISK", "UGX"
    );

    private StripeLifecycleMapping() {
    }

    // Converts a Stripe amount in minor units into the domain Money representation.
    public static Money moneyFromMinorUnits(
            Long amountInMinorUnits,
            String currencyCode,
            String materialFact
    ) {
        if (amountInMinorUnits == null) {
            throw new PaymentGatewayException(
                    "Stripe response is missing " + materialFact
            );
        }

        Currency currency = currency(currencyCode);
        int fractionDigits = stripeFractionDigits(currency);

        return Money.of(
                BigDecimal.valueOf(amountInMinorUnits, fractionDigits),
                currency
        );
    }

    // Converts the domain Money value into Stripe's minor-unit representation.
    public static long moneyToMinorUnits(Money money) {
        int fractionDigits = stripeFractionDigits(money.currency());

        try {
            return money.amount()
                    .movePointRight(fractionDigits)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new PaymentGatewayException(
                    "Amount cannot be represented in Stripe minor units: " + money
            );
        }
    }

    // Maps Stripe error details into the domain PaymentFailure model.
    public static PaymentFailure failure(
            String errorType,
            String providerCode,
            String providerDeclineCode,
            String detail
    ) {
        // Use a generic gateway error when Stripe provides no failure details.
        if (errorType == null
                && providerCode == null
                && providerDeclineCode == null
                && detail == null) {
            return PaymentFailure.of(
                    PaymentFailureCode.GATEWAY_ERROR,
                    "Stripe reported a payment failure without error details"
            );
        }

        return PaymentFailure.fromProvider(
                classifyFailure(errorType, providerCode, providerDeclineCode),
                providerCode,
                providerDeclineCode,
                detail
        );
    }

    // Maps Stripe cancellation reasons into domain cancellation reasons.
    public static PaymentCancellation cancellation(String providerReason) {
        PaymentCancellationReason reason =
                switch (providerReason == null ? "" : providerReason) {
                    case "requested_by_customer", "abandoned" ->
                            PaymentCancellationReason.CUSTOMER_REQUESTED;

                    case "expired" ->
                            PaymentCancellationReason.AUTHORIZATION_EXPIRED;

                    default ->
                            PaymentCancellationReason.PROVIDER_CANCELLED;
                };

        return new PaymentCancellation(
                reason,
                providerReason,
                "Stripe cancelled the payment intent"
        );
    }

    // Maps Stripe next-action data into the domain RequiredPaymentAction model.
    public static RequiredPaymentAction requiredAction(
            String type,
            String redirectUrl
    ) {
        if (type == null || type.isBlank()) {
            throw new PaymentGatewayException(
                    "Stripe requires_action response is missing next_action.type"
            );
        }

        URI redirectUri = null;

        // Validate and parse the redirect URL when Stripe provides one.
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            try {
                redirectUri = URI.create(redirectUrl);
            } catch (IllegalArgumentException exception) {
                throw new PaymentGatewayException(
                        "Stripe next_action redirect URL is invalid"
                );
            }

            // Stripe redirect URLs must be absolute URLs.
            if (!redirectUri.isAbsolute()) {
                throw new PaymentGatewayException(
                        "Stripe next_action redirect URL must be absolute"
                );
            }
        }

        // redirect_to_url actions are invalid without an actual redirect URL.
        if ("redirect_to_url".equals(type) && redirectUri == null) {
            throw new PaymentGatewayException(
                    "Stripe redirect_to_url action is missing its redirect URL"
            );
        }

        return new RequiredPaymentAction(type, redirectUri);
    }

    // Classifies Stripe-specific error information into domain failure categories.
    private static PaymentFailureCode classifyFailure(
            String errorType,
            String providerCode,
            String providerDeclineCode
    ) {
        if ("payment_method_provider_timeout".equals(providerCode)) {
            return PaymentFailureCode.TIMEOUT;
        }

        if ("invalid_request_error".equals(errorType)) {
            return PaymentFailureCode.VALIDATION_ERROR;
        }

        if (providerDeclineCode != null
                || "card_error".equals(errorType)
                || "card_declined".equals(providerCode)
                || "payment_method_provider_decline".equals(providerCode)) {
            return PaymentFailureCode.DECLINED;
        }

        // Unknown Stripe failures are treated as generic gateway errors.
        return PaymentFailureCode.GATEWAY_ERROR;
    }

    // Converts and validates Stripe's currency code as a Java Currency.
    private static Currency currency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new PaymentGatewayException(
                    "Stripe response is missing currency"
            );
        }

        try {
            return Currency.getInstance(
                    currencyCode.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new PaymentGatewayException(
                    "Stripe response contains unsupported currency: " + currencyCode
            );
        }
    }

    // Returns the number of decimal places Stripe expects for the currency.
    private static int stripeFractionDigits(Currency currency) {
        String currencyCode = currency.getCurrencyCode();

        if (ZERO_DECIMAL_CURRENCIES.contains(currencyCode)) {
            return 0;
        }

        if (TWO_DECIMAL_STRIPE_SPECIAL_CASES.contains(currencyCode)) {
            return 2;
        }

        // Fall back to Java's standard currency fraction digits.
        int fractionDigits = currency.getDefaultFractionDigits();

        if (fractionDigits < 0) {
            throw new PaymentGatewayException(
                    "Stripe currency does not have a supported fraction size: "
                            + currencyCode
            );
        }

        return fractionDigits;
    }
}