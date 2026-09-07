package com.emrekiziltoprak.payment.gateway.service.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentCancelled;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentCaptured;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentFailed;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentInitiated;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentProcessing;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentRefunded;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentRequiresAction;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.ProviderReferenceConflictException;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ActionRequiredObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.AuthorizationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CancellationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CaptureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.FailureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.LifecycleObservationContext;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ProcessingObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.RequiredPaymentAction;

import lombok.Getter;

@Getter
public class Payment {

    private final PaymentId id;
    private final AccountId sourceAccountId;
    private final AccountId destinationAccountId;
    private final Money amount;

    private ProviderPaymentReference paymentRef;
    private final Instant createdAt;
    private final List<PaymentEvent> domainEvents = new ArrayList<>();

    private PaymentStatus status;
    private PaymentFailure failure;
    private PaymentCancellationReason cancellationReason;

    private Instant updatedAt;

    private Payment(PaymentId id,
                    AccountId sourceAccountId,
                    AccountId destinationAccountId,
                    String referenceId,
                    Money amount,
                    PaymentProvider paymentProvider,
                    PaymentStatus status,
                    Instant createdAt,
                    Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId cannot be null");
        this.destinationAccountId = Objects.requireNonNull(destinationAccountId, "destinationAccountId cannot be null");
        this.amount = Objects.requireNonNull(amount, "amount cannot be null");
        this.paymentRef = new ProviderPaymentReference(paymentProvider, referenceId);
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public static Payment initiate(PaymentId id,
                                   AccountId sourceAccountId,
                                   AccountId destinationAccountId,
                                   Money amount,
                                   PaymentProvider paymentProvider) {
        Instant initiatedAt = Instant.now();
        Payment payment = new Payment(
                id,
                sourceAccountId,
                destinationAccountId,
                null,
                amount,
                paymentProvider,
                PaymentStatus.INITIATED,
                initiatedAt,
                initiatedAt
        );

        payment.addDomainEvent(new PaymentInitiated(
                id,
                sourceAccountId,
                destinationAccountId,
                amount,
                initiatedAt
        ));
        return payment;
    }

    public static Payment restore(PaymentId id,
                                  AccountId sourceAccountId,
                                  AccountId destinationAccountId,
                                  String referenceId,
                                  Money amount,
                                  PaymentProvider paymentProvider,
                                  PaymentStatus status,
                                  Instant createdAt,
                                  Instant updatedAt) {
        PaymentFailure restoredFailure = status == PaymentStatus.FAILED
                ? PaymentFailure.of(PaymentFailureCode.GATEWAY_ERROR, "Failure detail unavailable")
                : null;
        return restore(id, sourceAccountId, destinationAccountId, referenceId, amount,
                paymentProvider, status, restoredFailure, null, createdAt, updatedAt);
    }

    public static Payment restore(PaymentId id,
                                  AccountId sourceAccountId,
                                  AccountId destinationAccountId,
                                  String referenceId,
                                  Money amount,
                                  PaymentProvider paymentProvider,
                                  PaymentStatus status,
                                  PaymentFailure failure,
                                  Instant createdAt,
                                  Instant updatedAt) {
        return restore(id, sourceAccountId, destinationAccountId, referenceId, amount,
                paymentProvider, status, failure, null, createdAt, updatedAt);
    }

    public static Payment restore(PaymentId id,
                                  AccountId sourceAccountId,
                                  AccountId destinationAccountId,
                                  String referenceId,
                                  Money amount,
                                  PaymentProvider paymentProvider,
                                  PaymentStatus status,
                                  PaymentFailure failure,
                                  PaymentCancellationReason cancellationReason,
                                  Instant createdAt,
                                  Instant updatedAt) {
        if (status == PaymentStatus.FAILED && failure == null) {
            throw new IllegalArgumentException("Failed payment must have failure information");
        }
        if (status != PaymentStatus.CANCELLED && cancellationReason != null) {
            throw new IllegalArgumentException(
                    "Cancellation reason is only valid for cancelled payments"
            );
        }
        Payment payment = new Payment(
                id,
                sourceAccountId,
                destinationAccountId,
                referenceId,
                amount,
                paymentProvider,
                status,
                createdAt,
                updatedAt
        );
        payment.failure = failure;
        payment.cancellationReason = cancellationReason;
        return payment;
    }

    public void assignProviderReference(ProviderPaymentReference newPaymentRef) {
        Objects.requireNonNull(newPaymentRef, "providerReference cant be null");

        if (!paymentRef.provider().equals(newPaymentRef.provider())) {
            throw new ProviderReferenceConflictException("Payment provider cannot be changed");
        }

        if (paymentRef.hasValue() && !paymentRef.equals(newPaymentRef)) {
            throw new ProviderReferenceConflictException("Provider reference cannot be replaced");
        }

        if (!paymentRef.hasValue()) {
            this.paymentRef = newPaymentRef;
            this.updatedAt = Instant.now();
        }
    }

    public void assignProviderReference(String referenceId) {
        assignProviderReference(new ProviderPaymentReference(paymentRef.provider(), referenceId));
    }

    public String getReferenceId() {
        return paymentRef.value();
    }

    public PaymentProvider getPaymentProvider() {
        return paymentRef.provider();
    }

    public void markAsCaptured() {
        if (status == PaymentStatus.CAPTURED) {
            return;
        }
        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PROCESSING
                && status != PaymentStatus.REQUIRES_ACTION
                && status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment can only be marked as captured from INITIATED or PROCESSING or AUTHORIZED status");
        }
        status = PaymentStatus.CAPTURED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentCaptured(id, updatedAt));
    }

    public void markAsAuthorized() {
        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PROCESSING
                && status != PaymentStatus.REQUIRES_ACTION) {
            throw new IllegalStateException("Payment can only be marked as authorized from INITIATED, PROCESSING or REQUIRES_ACTION status");
        }
        status = PaymentStatus.AUTHORIZED;
        updatedAt = Instant.now();
    }

    public void markAsProcessing(String reason) {
        if (status == PaymentStatus.PROCESSING) {
            return;
        }
        if (status != PaymentStatus.INITIATED
            && status != PaymentStatus.REQUIRES_ACTION
        ) {
            throw new IllegalStateException("Payment can only be marked as pending from INITIATED status");
        }
        status = PaymentStatus.PROCESSING;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentProcessing(id, reason, updatedAt));
    }

    public void markAsRequiredAction(String reason) {
        if (status == PaymentStatus.REQUIRES_ACTION) {
            return;
        }

        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Payment can only require action from PROCESSING status"
            );
        }
        status = PaymentStatus.REQUIRES_ACTION;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentRequiresAction(id, reason, updatedAt));
    }

    public void markAsFailed(PaymentFailure failure) {
        if (status == PaymentStatus.FAILED) {
            return;
        }
        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PROCESSING
                && status != PaymentStatus.REQUIRES_ACTION
                && status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment can only be marked as failed from INITIATED or PENDING status");
        }
        this.failure = Objects.requireNonNull(failure, "failure cannot be null");
        status = PaymentStatus.FAILED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentFailed(id, failure, updatedAt));
    }

    public void markAsFailed(String reason) {
        markAsFailed(PaymentFailure.of(PaymentFailureCode.DECLINED, reason));
    }

    public void markAsCancelled(PaymentCancellationReason reason) {
        if (status == PaymentStatus.CANCELLED) {
            return;
        }

        if (status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Payment cannot be cancelled from status: " + status
            );
        }
        cancellationReason = Objects.requireNonNull(reason, "cancellationReason cannot be null");
        status = PaymentStatus.CANCELLED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentCancelled(id, cancellationReason, updatedAt));
    }

    public TransitionResult observe(PaymentLifecycleObservation observation) {
        Objects.requireNonNull(observation, "observation cannot be null");

        // Extract the shared observation context used for validation and transition handling.
        LifecycleObservationContext context = observation.context();

        // Validate that the observation belongs to this payment before applying any state change.
        List<String> conflictDetails = validateObservationFacts(context);
        if (!conflictDetails.isEmpty()) {
            return new TransitionResult.Conflict(conflictDetails);
        }

        // Prefer the provider reference received from the observation.
        // Fall back to the payment's existing reference when the observed reference has no value.
        ProviderPaymentReference observedReference = context.providerReference().hasValue()
                ? context.providerReference()
                : paymentRef;

        // Resolve the effective timestamp to be used for the state transition.
        Instant occurredAt = context.effectiveOccurredAt();

        // Route the observation to the corresponding domain transition.
        return switch (observation) {
            case ProcessingObservation ignored ->
                    observeProcessing(observedReference, occurredAt);

            case ActionRequiredObservation actionRequired ->
                    observeRequiresAction(
                            observedReference,
                            actionDescription(actionRequired.action()),
                            occurredAt
                    );

            case AuthorizationObservation ignored ->
                    observeAuthorization(observedReference, occurredAt);

            case CaptureObservation ignored ->
                    observeCapture(observedReference, context.amount(), occurredAt);

            case FailureObservation failureObservation ->
                    observeFailure(
                            observedReference,
                            failureObservation.failure(),
                            occurredAt
                    );

            case CancellationObservation cancellationObservation ->
                    observeCancellation(
                            observedReference,
                            cancellationObservation.cancellation().reason(),
                            occurredAt
                    );
        };
    }

    private List<String> validateObservationFacts(LifecycleObservationContext context) {
        // Collect all mismatches so the caller can return a complete conflict result.
        List<String> conflicts = new ArrayList<>();

        // If an internal payment ID is provided, it must match this payment.
        context.internalPaymentId()
                .filter(observedPaymentId -> !id.equals(observedPaymentId))
                .ifPresent(observedPaymentId -> conflicts.add(
                        "Payment ID mismatch. Expected: " + id.value()
                                + " Got: " + observedPaymentId.value()
                ));

        // The observation must come from the same payment provider.
        boolean providerMismatch =
                !paymentRef.provider().equals(context.providerReference().provider());

        if (providerMismatch) {
            conflicts.add(
                    "Provider mismatch. Expected: " + paymentRef.provider()
                            + " Got: " + context.providerReference().provider()
            );
        }

        // Only compare provider references when the providers already match.
        // A missing reference is not considered a conflict because it may be assigned later.
        if (!providerMismatch && providerReferencesConflict(context.providerReference())) {
            conflicts.add(
                    "Provider reference mismatch. Expected: " + paymentRef.value()
                            + " Got: " + context.providerReference().value()
            );
        }

        // The observed amount must match the amount stored on the payment.
        if (!amount.equals(context.amount())) {
            conflicts.add(
                    "Amount mismatch. Expected: " + amount
                            + " Got: " + context.amount()
            );
        }

        return conflicts;
    }

    private String actionDescription(RequiredPaymentAction action) {
        // Prefer the redirect URI when available; otherwise use the action type as a fallback.
        return action.redirectUri() != null
                ? action.redirectUri().toString()
                : action.type();
    }

    public TransitionResult observeCapture(ProviderPaymentReference paymentRef, Money amount, Instant occuredOn) {
        Objects.requireNonNull(paymentRef, "Payment reference can not be null");
        Objects.requireNonNull(amount, "Amount can not be null");
        Objects.requireNonNull(occuredOn, "Occurence time can not be null");

        List<String> conflictDetails = new ArrayList<>();
        
        if (providerReferencesConflict(paymentRef)) {
            conflictDetails.add("Provider reference mismatch, Expected: " + this.paymentRef.value() + " Got: " + paymentRef.value()); 
        }
        if(this.amount != null && !this.amount.equals(amount)){
            conflictDetails.add("Amount mismatch, Expected: " + this.amount + " Got: " + amount); 
        }
       
        if(!conflictDetails.isEmpty()){
            return new TransitionResult.Conflict(conflictDetails);
        }
        

        /* Idempotency control: If statuses are the same then return IDEMPOTENT_NO_OP */
        if(this.status == PaymentStatus.CAPTURED) {
            return new TransitionResult.Idempotent();
        }

        /* If regressing or going to a wrong status */
        if(this.status == PaymentStatus.REFUNDED || this.status == PaymentStatus.PARTIALLY_REFUNDED) {
            return new TransitionResult.Stale("Capture callback would regress from " + this.status);
        }
        if(this.status == PaymentStatus.FAILED || this.status == PaymentStatus.CANCELLED) {
            return new TransitionResult.Stale("Payment is already in terminal status: " + this.status);
        }

        /* return the result as success */
        this.status = PaymentStatus.CAPTURED;
        this.updatedAt = occuredOn;

        if(!(this.paymentRef.hasValue())){
            this.paymentRef =  paymentRef;
        }
        addDomainEvent(new PaymentCaptured(this.id, occuredOn));
        return new TransitionResult.Applied();

    }

    public TransitionResult observeAuthorization(ProviderPaymentReference paymentRef, Instant occurredOn) {
        Objects.requireNonNull(paymentRef, "Payment reference can not be null");
        Objects.requireNonNull(occurredOn, "Occurrence time can not be null");

        if (providerReferencesConflict(paymentRef)) {
            return new TransitionResult.Conflict(
                    "Provider reference mismatch, Expected: " + this.paymentRef.value()
                            + " Got: " + paymentRef.value()
            );
        }

        if (this.status == PaymentStatus.AUTHORIZED) {
            return new TransitionResult.Idempotent();
        }

        if (this.status == PaymentStatus.CAPTURED
                || this.status == PaymentStatus.REFUNDED
                || this.status == PaymentStatus.PARTIALLY_REFUNDED) {
            return new TransitionResult.Stale(
                    "Authorization callback would regress from " + this.status
            );
        }

        if (this.status == PaymentStatus.FAILED || this.status == PaymentStatus.CANCELLED) {
            return new TransitionResult.Stale(
                    "Payment is already in terminal status: " + this.status
            );
        }

        if (!this.paymentRef.hasValue()) {
            this.paymentRef = paymentRef;
        }

        this.status = PaymentStatus.AUTHORIZED;
        this.updatedAt = occurredOn;

        return new TransitionResult.Applied();
    }

    public TransitionResult observeFailure(ProviderPaymentReference paymentRef, PaymentFailure failure, Instant occurredOn){

        Objects.requireNonNull(paymentRef, "Payment reference can not be null");
        Objects.requireNonNull(failure, "Payment failure can not be null");
        Objects.requireNonNull(occurredOn, "occurredOn can not be null");

        if (providerReferencesConflict(paymentRef)) {
            return new TransitionResult.Conflict("Provider reference mismatch, Expected: " + this.paymentRef.value() + " Got: " + paymentRef.value());
        }
        
        if(this.status == PaymentStatus.FAILED) {
            return new TransitionResult.Idempotent();
        }

        if(this.status == PaymentStatus.CAPTURED ||
            this.status == PaymentStatus.REFUNDED ||
            this.status == PaymentStatus.PARTIALLY_REFUNDED ||
             this.status == PaymentStatus.CANCELLED
         ){
            return new TransitionResult.Stale("Failure callback would regress from " + this.status);
         }
         bindProviderReferenceIfPresent(paymentRef);
         this.failure = failure;
         this.status = PaymentStatus.FAILED;
         this.updatedAt = occurredOn;

         addDomainEvent(new PaymentFailed(this.id, failure, occurredOn));

         return new TransitionResult.Applied();

    }

    public TransitionResult observeCancellation(
            ProviderPaymentReference paymentRef,
            PaymentCancellationReason reason,
            Instant occurredOn
    ) {
        Objects.requireNonNull(paymentRef, "Payment reference cannot be null");
        Objects.requireNonNull(reason, "Cancellation reason cannot be null");
        Objects.requireNonNull(occurredOn, "Occurrence time cannot be null");

        if (providerReferencesConflict(paymentRef)) {
            return new TransitionResult.Conflict(
                    "Provider reference mismatch. Expected: " + this.paymentRef.value() + " Got: " + paymentRef.value()
            );
        }

        if (this.status == PaymentStatus.CANCELLED) {
            if (this.cancellationReason == reason) {
                return new TransitionResult.Idempotent();
            }

            return new TransitionResult.Conflict(
                    "Cancellation reason mismatch. Expected: "
                            + this.cancellationReason + " Got: " + reason
            );
        }

        if (this.status == PaymentStatus.CAPTURED ||
                this.status == PaymentStatus.REFUNDED ||
                this.status == PaymentStatus.PARTIALLY_REFUNDED ||
                this.status == PaymentStatus.FAILED) {
            return new TransitionResult.Stale("Cancellation callback would regress from " + this.status);
        }

        bindProviderReferenceIfPresent(paymentRef);
        this.status = PaymentStatus.CANCELLED;
        this.cancellationReason = reason;
        this.updatedAt = occurredOn;

        addDomainEvent(new PaymentCancelled(this.id, reason, occurredOn));

        return new TransitionResult.Applied();
    }

    public TransitionResult observeRequiresAction(ProviderPaymentReference paymentRef, String actionUrl, Instant occuredOn){
       Objects.requireNonNull(paymentRef, "Payment reference can not be null");
       Objects.requireNonNull(actionUrl, "actionUrl can not be null");
       Objects.requireNonNull(occuredOn, "Occurence time can not be null");
        
        if (providerReferencesConflict(paymentRef)) {
            return new TransitionResult.Conflict("Provider reference mismatch, Expected: " + this.paymentRef.value() + " Got: " + paymentRef.value());
        }
        
        if(this.status == PaymentStatus.REQUIRES_ACTION) {
            return new TransitionResult.Idempotent();
        }
        
        
        if(this.status == PaymentStatus.AUTHORIZED ||
             this.status == PaymentStatus.CAPTURED) {
            return new TransitionResult.Stale("RequireAction callback would regress from " + this.status);
             }

        if(this.status == PaymentStatus.REFUNDED || this.status == PaymentStatus.PARTIALLY_REFUNDED) {
            return new TransitionResult.Stale("RequireAction callback would regress from " + this.status);
        }
        if(this.status == PaymentStatus.FAILED || this.status == PaymentStatus.CANCELLED) {
            return new TransitionResult.Stale("Payment is already in terminal status: " + this.status);
        }

        bindProviderReferenceIfPresent(paymentRef);
        this.status = PaymentStatus.REQUIRES_ACTION;
        this.updatedAt = occuredOn;

        addDomainEvent(new PaymentRequiresAction(this.id, actionUrl, occuredOn));
    
        return new TransitionResult.Applied();
    }


    public TransitionResult observeProcessing(ProviderPaymentReference paymentRef, Instant occuredOn){
        Objects.requireNonNull(paymentRef, "Payment reference can not be null");
        Objects.requireNonNull(occuredOn, "Occurence time can not be null");

        if (providerReferencesConflict(paymentRef)) {
            return new TransitionResult.Conflict("Provider reference mismatch, Expected: " + this.paymentRef.value() + " Got: " + paymentRef.value());
        }

        if(this.status == PaymentStatus.PROCESSING) {
            return new TransitionResult.Idempotent();
        }

        if(    
            this.status == PaymentStatus.CAPTURED || this.status == PaymentStatus.AUTHORIZED || 
            this.status == PaymentStatus.REFUNDED || this.status == PaymentStatus.PARTIALLY_REFUNDED) {
            return new TransitionResult.Stale("Processing callback would regress from " + this.status);
        }
         if(this.status == PaymentStatus.FAILED || this.status == PaymentStatus.CANCELLED) {
            return new TransitionResult.Stale("Payment is already in terminal status: " + this.status);
        }

        bindProviderReferenceIfPresent(paymentRef);
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = occuredOn;

        addDomainEvent(new PaymentProcessing(this.id, null, occuredOn));

        return new TransitionResult.Applied();
    }

    private boolean providerReferencesConflict(ProviderPaymentReference observedReference) {
        if (!paymentRef.provider().equals(observedReference.provider())) {
            return true;
        }

        return paymentRef.hasValue()
                && observedReference.hasValue()
                && !paymentRef.equals(observedReference);
    }

    private void bindProviderReferenceIfPresent(ProviderPaymentReference observedReference) {
        if (!paymentRef.hasValue() && observedReference.hasValue()) {
            paymentRef = observedReference;
        }
    }


    public void refund() {
        if (status != PaymentStatus.CAPTURED
                && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Only captured or partially refunded payments can be refunded");
        }
        status = PaymentStatus.REFUNDED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentRefunded(id, updatedAt));
    }

    public List<PaymentEvent> getDomainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    private void addDomainEvent(PaymentEvent event) {
        domainEvents.add(event);
    }

}
