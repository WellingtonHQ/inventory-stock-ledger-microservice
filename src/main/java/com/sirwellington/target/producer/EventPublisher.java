package com.sirwellington.target.producer;

import java.text.MessageFormat;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sirwellington.target.model.EventPayload;
import com.sirwellington.target.rest.OperationFailedException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(EventPublisher.class);
    private static final String TOPIC = KafkaProducerConfig.TOPIC;

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public EventPublisher(
        KafkaProducer<String, String> producer,
        ObjectMapper objectMapper
    ) {
        Objects.requireNonNull(producer);
        Objects.requireNonNull(objectMapper);
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    public void publish(EventPayload payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException ex) {
            var message = MessageFormat.format(
                "Failed to serialize event payload for sku [{0}]",
                payload.skuId()
            );
            LOG.error(message, ex);
            throw new OperationFailedException(message, ex);
        }

        var message = new ProducerRecord<>(TOPIC, payload.skuId(), payloadJson);
        var future = producer.send(message);

        try {
            // We want to wait until we know for sure Kafka published the message.
            // TODO: future.get() may stall, so let's add a timeout to prevent this thread getting stuck permanently.
            // TODO: We may want to add some kind of offline ROLLBACK operation to undo any database saves,
            // or we could put them in a dead-letter-queue.
            var metadata = future.get();
            LOG.info(
                "Published event: sku={}, partition={}, offset={}",
                payload.skuId(),
                metadata.partition(),
                metadata.offset()
            );
        }
        catch (Exception ex) {
            var errorMessage = MessageFormat.format("Failed to publish event for sku [{0}]", payload.skuId());
            LOG.error(errorMessage, ex);
            throw new OperationFailedException(errorMessage, ex);
        }
    }

    public void close() {
        producer.flush();
        producer.close();
    }
}
