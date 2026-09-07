# Provider lifecycle observation mappings

This document defines how active payment-provider adapters translate provider responses into provider-neutral lifecycle observations.

## Responsibility boundary

Adapters authenticate or deserialize provider payloads, validate the material facts needed for correlation, and translate provider statuses into typed `PaymentLifecycleObservation` values. They do not decide whether an observation is a valid aggregate transition, a duplicate, stale, or conflicting.

The `Payment` aggregate makes those lifecycle decisions when it receives an observation. Provider status strings therefore never enter `PaymentStatus` and adapters never mutate aggregate lifecycle state directly.

Every emitted observation contains a `LifecycleObservationContext` with:

| Fact | Meaning |
| --- | --- |
| `internalPaymentId` | Internal correlation ID when the provider interaction supplies one. |
| `providerReference` | Provider identity and provider payment ID. A response-level Iyzico failure may legitimately lack the payment ID. |
| `amount` | Provider-reported amount and currency when required; documented fallbacks apply only to Iyzico failure responses. |
| `observedAt` | Time at which this service translated the response or event. |
| `providerOccurredAt` | Provider occurrence time when the provider supplies one. The aggregate uses it in preference to `observedAt`. |

## Stripe webhook events

Only the following signed `PaymentIntent` events produce lifecycle observations:

| Stripe event | Observation | Amount fact | Additional facts |
| --- | --- | --- | --- |
| `payment_intent.processing` | `ProcessingObservation` | `amount` + `currency` | PaymentIntent ID is the provider reference. |
| `payment_intent.requires_action` | `ActionRequiredObservation` | `amount` + `currency` | `next_action.type` is required. `redirect_to_url.url` is carried when present and must be an absolute URI; it is required when the type is `redirect_to_url`. |
| `payment_intent.amount_capturable_updated` | `AuthorizationObservation` | `amount_capturable` + `currency` | Represents funds authorized for the manual-capture path. |
| `payment_intent.succeeded` | `CaptureObservation` | `amount_received` + `currency` | Represents captured funds, including completion of either automatic or manual capture. These facts allow the aggregate to detect duplicate or conflicting capture reports. |
| `payment_intent.payment_failed` | `FailureObservation` | `amount` + `currency` | `last_payment_error` becomes structured failure information. If Stripe omits it, a generic `GATEWAY_ERROR` failure is emitted. |
| `payment_intent.canceled` | `CancellationObservation` | `amount` + `currency` | `cancellation_reason` becomes structured cancellation information; cancellation is not represented as a failure. |

For all supported events, `event.created` is converted from epoch seconds to `providerOccurredAt`. A valid UUID in PaymentIntent metadata key `payment_id` is used as `internalPaymentId`. Missing metadata leaves that optional correlation fact empty, allowing lookup by the Stripe provider reference; a supplied but malformed UUID is rejected at the adapter boundary.

An unrelated or unknown event type returns no observation. After signature verification, the webhook endpoint acknowledges it with HTTP 200 and does not invoke lifecycle processing.

A supported event is rejected at the adapter boundary when its PaymentIntent cannot be deserialized or lacks a required provider reference, amount, currency, or action fact. No observation is delivered, and the webhook endpoint returns HTTP 500 so Stripe can retry. Missing or invalid signatures return HTTP 401.

## Stripe synchronous responses

The Stripe outbound adapter maps the immediate PaymentIntent result as follows:

| Stripe status | Observation | Material facts and path |
| --- | --- | --- |
| `succeeded` | `CaptureObservation` | `id`, `amount_received`, and `currency`; this is the automatic-capture completion path. |
| `requires_capture` | `AuthorizationObservation` | `id`, `amount_capturable`, and `currency`; this is the manual-capture authorization path. A later successful capture is reported as a capture observation. |
| `requires_action` | `ActionRequiredObservation` | `id`, `amount`, `currency`, and validated `next_action` details. |
| `processing` | `ProcessingObservation` | `id`, `amount`, and `currency`. |
| `requires_payment_method` | `FailureObservation` | `id`, `amount`, `currency`, and required `last_payment_error`. |
| `canceled` | `CancellationObservation` | `id`, `amount`, `currency`, and structured `cancellation_reason`. `canceled_at`, when present, becomes `providerOccurredAt`. |

The internal payment ID is known from the payment being submitted. Stripe synchronous results do not otherwise supply a mapped provider occurrence time; `observedAt` is used, except for `canceled_at` as noted above.

`requires_confirmation` and any other unsupported Stripe status are rejected instead of being guessed into a lifecycle state. A null response, missing status, missing provider reference, missing status-specific amount, missing currency, or malformed action also raises a boundary error and emits no observation. A `requires_payment_method` result without `last_payment_error` is rejected because it lacks the required structured failure facts.

Stripe amounts are converted between provider minor units and `Money` using Stripe currency exponents, including zero-decimal currencies and Stripe's two-decimal handling for ISK and UGX.

### Stripe failure mapping

Stripe `last_payment_error.code` and `last_payment_error.decline_code` are preserved respectively as `PaymentFailure.providerCode` and `PaymentFailure.providerDeclineCode`; the provider message is preserved as `detail`.

| Stripe error fact | Domain failure code |
| --- | --- |
| `code = payment_method_provider_timeout` | `TIMEOUT` |
| `type = invalid_request_error` | `VALIDATION_ERROR` |
| A decline code is present, `type = card_error`, `code = card_declined`, or `code = payment_method_provider_decline` | `DECLINED` |
| Any other error combination | `GATEWAY_ERROR` |

### Stripe cancellation mapping

The raw Stripe cancellation reason is preserved as `PaymentCancellation.providerReason`.

| Stripe cancellation reason | Domain cancellation reason |
| --- | --- |
| `requested_by_customer`, `abandoned` | `CUSTOMER_REQUESTED` |
| `expired` | `AUTHORIZATION_EXPIRED` |
| Missing or any other value | `PROVIDER_CANCELLED` |

## Iyzico synchronous responses

Iyzico first reports request execution through `status`; a successful execution is then interpreted using `fraudStatus`.

| Iyzico response | Observation | Material facts and meaning |
| --- | --- | --- |
| `status = success`, `fraudStatus = 1` | `CaptureObservation` | `paymentId`, `price`, and `currency` are required. The provider has accepted and captured the payment. |
| `status = success`, `fraudStatus = 0` | `ProcessingObservation` | `paymentId`, `price`, and `currency` are required. The payment remains under fraud review; the adapter does not promote it to success. |
| `status = success`, `fraudStatus = -1` | `FailureObservation` | Structured `DECLINED` failure. Provider error fields are retained when present; otherwise `fraud_status_-1` and a fraud-rejection detail are supplied. |
| `status = failure` | `FailureObservation` | Structured response-level failure classified from `errorCode` and `errorGroup`. This response may omit `paymentId`, `price`, or `currency`. |

For success responses, Iyzico `price` is the correlated payment amount; `paidPrice` is not used as the lifecycle amount. For a failure response, valid response `price` and `currency` are used when both are present; otherwise the submitted payment's amount is retained. The adapter does not fabricate a provider payment ID or substitute `conversationId` for it.

Iyzico `systemTime` is converted from epoch milliseconds to `providerOccurredAt`. The submitted payment ID is always available as `internalPaymentId`. When a nonblank response `conversationId` is present, it must match that payment ID.

### Iyzico failure mapping

Iyzico `errorCode` is preserved as `PaymentFailure.providerCode`, `errorGroup` as `PaymentFailure.providerDeclineCode`, and `errorMessage` as `detail`.

| Iyzico error fact | Domain failure code |
| --- | --- |
| `errorCode` starts with `10`, is `6000` or `6001`, or `errorGroup` contains `DECLIN` or `NOT_SUFFICIENT` | `DECLINED` |
| `errorGroup` contains `TIMEOUT` | `TIMEOUT` |
| `errorGroup` contains `VALIDATION` or `INVALID` | `VALIDATION_ERROR` |
| Any other response-level failure | `GATEWAY_ERROR` |

An Iyzico success response missing `fraudStatus`, or a capture/processing response missing `paymentId`, `price`, or `currency`, is rejected at the adapter boundary. Unsupported top-level statuses, unsupported fraud statuses, invalid amount/currency facts, and conflicting `conversationId` values are likewise rejected without emitting an observation.

Network failures, client transport errors, and gateway timeouts are surfaced as gateway timeout exceptions rather than provider lifecycle observations; no aggregate transition is chosen by the adapter in those cases.
