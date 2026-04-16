package com.artha.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Routes failed messages to a DLQ topic so they can be inspected out-of-band
 * without blocking the consumer group. The DLQ topic name is the source topic
 * suffixed with ".dlq" — a simple convention that's easy to script against.
 * <p>
 * Along with the payload we attach the original topic, partition, offset, and
 * the exception class+message as Kafka headers — enough context for an operator
 * to replay or triage.
 */
@Component
public class DeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterPublisher.class);
    private static final String DLQ_SUFFIX = ".dlq";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public DeadLetterPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendToDlq(ConsumerRecord<String, String> record, Throwable cause) {
        String dlqTopic = record.topic() + DLQ_SUFFIX;
        try {
            var producerRecord = new org.apache.kafka.clients.producer.ProducerRecord<>(
                    dlqTopic, record.partition(), record.key(), record.value()
            );
            producerRecord.headers().add("original-topic", record.topic().getBytes());
            producerRecord.headers().add("original-offset", String.valueOf(record.offset()).getBytes());
            producerRecord.headers().add("exception-class", cause.getClass().getName().getBytes());
            producerRecord.headers().add("exception-message",
                    (cause.getMessage() == null ? "" : cause.getMessage()).getBytes());
            // Copy original headers for context
            record.headers().forEach(h -> producerRecord.headers().add(h));
            kafkaTemplate.send(producerRecord);
            log.warn("Routed message offset={} from {} to DLQ {}: {}", record.offset(),
                    record.topic(), dlqTopic, cause.getMessage());
        } catch (Exception e) {
            // If even the DLQ is unreachable we log loudly but don't throw — better to
            // skip the poison message than to stop the whole consumer group.
            log.error("Failed to route to DLQ {} (dropping poison message): {}", dlqTopic, e.getMessage(), e);
        }
    }
}
