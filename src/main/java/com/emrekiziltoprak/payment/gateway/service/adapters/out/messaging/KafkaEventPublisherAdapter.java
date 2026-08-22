package com.emrekiziltoprak.payment.gateway.service.adapters.out.messaging;

import com.emrekiziltoprak.payment.gateway.service.ports.out.EventPublisherPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.OutboxMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaEventPublisherAdapter(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${payment.events.topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(OutboxMessage message) {
        ProducerRecord<String, String> record =
                createProducerRecord(message);

        try {
            kafkaTemplate.send(record)
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException exception) {
            throw new IllegalStateException(
                    "Could not publish outbox message: "
                            + message.id(),
                    exception.getCause()
            );
        }
    }

    private ProducerRecord<String, String> createProducerRecord(
            OutboxMessage message
    ) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        topic,
                        message.id().toString(),
                        message.payload()
                );

        record.headers().add(
                "eventId",
                message.id().toString()
                        .getBytes(StandardCharsets.UTF_8)
        );

        record.headers().add(
                "eventType",
                message.eventType()
                        .getBytes(StandardCharsets.UTF_8)
        );

        record.headers().add(
                "eventCreatedAt",
                message.createdAt().toString()
                        .getBytes(StandardCharsets.UTF_8)
        );

        return record;
    }
}