# Payment Gateway Service

A Spring Boot payment orchestration service built with hexagonal (ports & adapters)
architecture. It accepts payment requests over HTTP, routes them to a pluggable
payment provider (Stripe, with Iyzico stubbed as a second strategy), reconciles
asynchronous provider callbacks via webhooks, and guarantees exactly-once event
delivery downstream through the transactional outbox pattern.

This project was built as a portfolio piece to demonstrate production-grade
backend design: strict domain isolation, idempotent request handling, and
reliable event publishing under failure — not just a CRUD wrapper around Stripe.

## Architecture

The codebase follows hexagonal architecture: business logic in `domain` and
`application` has zero dependency on frameworks, databases, or external SDKs.
Everything framework-specific lives behind a port and is swapped in at the
edges by `config.BeanConfiguration`.

```
              ┌────────────────────────────┐
              │           domain           │
              │  Payment, Money, events…   │
              └─────────────▲──────────────┘
                             │
              ┌──────────────┴──────────────┐
              │        ports.in / out       │
              │ ProcessPaymentUseCase,      │
              │ PaymentGatewayPort, …       │
              └──────────────▲──────────────┘
                             │
              ┌──────────────┴──────────────┐
              │         application         │
              │      ProcessPaymentService  │
              └──────────────▲──────────────┘
                             │
        ┌────────────────────┴────────────────────┐
        │                                          │
┌───────┴────────┐                        ┌────────┴────────┐
│  adapters.in    │                        │   adapters.out   │
│  REST + webhook │                        │ Stripe / Iyzico  │
│                 │                        │ Kafka, JPA       │
└─────────────────┘                        └──────────────────┘
```

Dependencies only ever point inward. `domain` knows nothing about Spring,
JPA, or Stripe; `application` depends only on `ports`; adapters implement
those ports and are wired together exclusively in `config`.

A full class-level dependency graph (Mermaid) is available in
[`docs/dependency-graph.md`](docs/dependency-graph.md).

### Package layout

| Package | Responsibility |
|---|---|
| `domain` | `Payment` aggregate, value objects (`Money`, `AccountId`, `PaymentId`), domain events, exceptions |
| `ports.in` | Inbound use case contracts (`ProcessPaymentUseCase`, `ProcessPaymentCallbackUseCase`) |
| `ports.out` | Outbound contracts the application depends on (`PaymentGatewayPort`, `PaymentRepository`, `EventPublisherPort`, …) |
| `application` | Use case implementations — orchestration only, no infrastructure |
| `adapters.in.web` | REST controllers (`PaymentController`, `StripeWebhookController`) |
| `adapters.in.scheduling` / `.transaction` | Outbox poller, transactional callback wrapper |
| `adapters.out.gateway` | `StripePaymentAdapter`, `IyzicoPaymentAdapter`, `PaymentGatewayRouter` |
| `adapters.out.messaging` | Kafka event publisher |
| `adapters.out.persistence` | JPA repositories and entity mappers |
| `config` | Composition root — wires use cases to concrete adapters |

## Key design decisions

**Strategy-routed payment gateways.** `PaymentGatewayPort` is implemented by
both `StripePaymentAdapter` and `IyzicoPaymentAdapter`. `PaymentGatewayRouter`
is itself a `PaymentGatewayPort` (marked `@Primary`) that resolves the correct
adapter at runtime by `PaymentProvider`, so `ProcessPaymentService` never knows
which concrete gateway it's talking to. Adding a new provider means adding one
adapter class — no changes to application logic.

**Idempotency at the write path.** Every payment request requires an
`Idempotency-Key` header. `ProcessPaymentService` checks the key against
`IdempotencyRepository` before creating a new `Payment`; a retried request
with the same key returns the original `PaymentId` instead of double-charging.

**Transactional outbox for event delivery.** Domain events (`PaymentInitiated`,
`PaymentSucceeded`, `PaymentFailed`, …) are written to an outbox table in the
same transaction as the payment state change (`saveStateAndOutbox`), so a
crash between "payment updated" and "event published" is impossible.
`OutboxPoller` runs on a fixed delay and `OutboxPublisherService` drains
unprocessed rows to Kafka independently of the request thread.

**Webhook reconciliation, not polling.** `StripeWebhookController` verifies
the Stripe signature, maps provider-specific event types to a
provider-agnostic `CallbackStatus`, and only accepts callbacks for payments in
a non-terminal state (`INITIATED`, `PENDING`, `AUTHORIZED`) — duplicate
terminal callbacks are detected and dropped rather than reapplied.

**Gateway timeouts fail safe, not silent.** If the provider call times out or
throws, the payment is marked `PENDING`/`FAILED` and persisted with its
outbox event rather than left in an ambiguous state — reconciliation later
happens via the webhook path.

## Tech stack

- Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL (runtime), H2 (test)
- Apache Kafka (`spring-boot-starter-kafka`) for domain event publishing
- Stripe Java SDK for payment intents and webhook signature verification
- Lombok
- Docker / Docker Compose

## Getting started

### Prerequisites

- Java 21+
- Docker (for Postgres/Kafka, or use the provided `compose.yaml`)
- A Stripe account and webhook signing secret for local webhook testing

### Configuration

Copy `.env.example` to `.env` and fill in the required values:

```bash
cp .env.example .env
```

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://localhost:5432/payment_gateway` |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials | `postgres` / `postgres` |
| `JPA_DDL_AUTO` | Hibernate schema strategy | `update` |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret (`whsec_…`) | — (required) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address | `localhost:9092` |
| `PAYMENT_EVENTS_TOPIC` | Kafka topic for domain events | `payment-events` |
| `OUTBOX_POLLER_BATCH_SIZE` | Rows drained per poll cycle | `100` |
| `OUTBOX_POLLER_FIXED_DELAY_MS` | Poll interval in ms | `1000` |

### Run locally

```bash
./mvnw spring-boot:run
```

### Run with Docker Compose

```bash
docker compose up --build
```

The service listens on port `3000`.

### Run tests

```bash
./mvnw test
```

## API

### `POST /api/v1/payments`

Initiates a payment. Requires an `Idempotency-Key` header.

```bash
curl -X POST http://localhost:3000/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 5f2a1e40-3c2b-4b3e-9a0e-6a2d1e0f9b11" \
  -d '{
    "sourceAccountId": "b1f2c3d4-e5f6-4789-a0b1-c2d3e4f5a6b7",
    "destinationAccountId": "c2d3e4f5-a6b7-4890-b1c2-d3e4f5a6b7c8",
    "amount": 100.00,
    "currency": "USD",
    "provider": "stripe"
  }'
```

### `POST /api/v1/webhooks/stripe`

Receives Stripe `payment_intent.*` events. Validates the `Stripe-Signature`
header against `STRIPE_WEBHOOK_SECRET`; requests without a valid signature are
rejected with `401`.

## Known limitations / next steps

- `PaymentRequestMapper` is currently unimplemented — request-to-command
  mapping is done inline in `PaymentController`.
- Iyzico adapter is a structural stub, not yet integrated against the live API.
- No refund flow yet (`PaymentRefunded` event exists in the domain model but
  is not triggered from any use case).
- `PaymentRepositoryAdapter` currently owns mapping for payment, idempotency,
  and outbox persistence together — a candidate for splitting into
  per-aggregate adapters as the schema grows.
