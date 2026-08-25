package com.emrekiziltoprak.payment.gateway.service.application;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataOutboxRepository;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.SpringDataPaymentRepository;
import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "stripe.webhook.secret=whsec_test_dummy",
        "spring.datasource.url=jdbc:h2:mem:callback_concurrency",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "outbox.poller.fixed-delay-ms=600000"
})
class ProcessPaymentCallbackConcurrencyTests {

    @Autowired
    private ProcessPaymentCallbackUseCase callbackUseCase;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SpringDataPaymentRepository springDataPaymentRepository;

    @Autowired
    private SpringDataOutboxRepository springDataOutboxRepository;

    @BeforeEach
    void cleanDatabase() {
        springDataOutboxRepository.deleteAll();
        springDataPaymentRepository.deleteAll();
    }

    @Test
    void concurrentDuplicateCallbacksCreateOneOutboxEvent() throws Exception {
        Payment payment = new Payment(
                PaymentId.generate(),
                AccountId.generate(),
                AccountId.generate(),
                "pi_concurrent_123",
                new Money(new BigDecimal("25.00"), Currency.getInstance("TRY")),
                PaymentProvider.STRIPE,
                PaymentStatus.PENDING
        );
        paymentRepository.save(payment);

        ProcessPaymentCallbackCommand command = new ProcessPaymentCallbackCommand(
                PaymentProvider.STRIPE,
                "pi_concurrent_123",
                ProcessPaymentCallbackCommand.CallbackStatus.SUCCESS,
                null
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> processAfterSignal(command, ready, start));
            Future<?> second = executor.submit(() -> processAfterSignal(command, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(springDataPaymentRepository.findAll())
                .singleElement()
                .satisfies(entity -> assertThat(entity.getStatus()).isEqualTo("SUCCEEDED"));
        assertThat(springDataOutboxRepository.count()).isEqualTo(1);
    }

    private void processAfterSignal(
            ProcessPaymentCallbackCommand command,
            CountDownLatch ready,
            CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Callback start signal timed out");
            }
            callbackUseCase.processCallback(command);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Callback thread interrupted", exception);
        }
    }
}
