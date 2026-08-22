package com.emrekiziltoprak.payment.gateway.service.adapters.in.scheduling;

import com.emrekiziltoprak.payment.gateway.service.application.OutboxPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxPublisherService publisherService;
    private final int batchSize;

    public OutboxPoller(
            OutboxPublisherService publisherService,
            @Value("${outbox.poller.batch-size:100}") int batchSize
    ) {
        this.publisherService = publisherService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:1000}")
    public void poll() {
        try {
            int publishedCount = publisherService.publishPendingEvents(batchSize);

            if (publishedCount > 0) {
                log.info("Published {} outbox message(s)", publishedCount);
            }
        } catch (RuntimeException exception) {
            log.error("Outbox polling failed; messages will be retried", exception);
        }
    }
}
