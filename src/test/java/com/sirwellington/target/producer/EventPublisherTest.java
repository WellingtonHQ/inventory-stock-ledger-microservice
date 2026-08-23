package com.sirwellington.target.producer;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sirwellington.target.model.EventPayload;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tech.sirwellington.alchemy.test.AlchemyTest;
import tech.sirwellington.alchemy.test.generation.GenerateString;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@AlchemyTest
class EventPublisherTest {

    @Mock
    private KafkaProducer<String, String> producer;

    @GenerateString
    private String skuId;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventPayload createPayload() {
        return new EventPayload(
            1L,
            "RECEIPT",
            skuId,
            50,
            BigDecimal.valueOf(10.00)
        );
    }

    @Test
    void testPublishesEventSuccessfully() {
        var payload = createPayload();
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(mockMetadata()));

        var publisher = new EventPublisher(producer, objectMapper);
        publisher.publish(payload);

        verify(producer).send(any(ProducerRecord.class));
    }

    @Test
    void testPublishesAdjustmentEvent() {
        var payload = new EventPayload(
            2L,
            "ADJUSTMENT",
            skuId,
            -10,
            BigDecimal.valueOf(5.50)
        );
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(mockMetadata()));

        var publisher = new EventPublisher(producer, objectMapper);
        publisher.publish(payload);

        verify(producer).send(any(ProducerRecord.class));
    }

    @Test
    void testPublishesSaleEvent() {
        var payload = new EventPayload(
            3L,
            "SALE",
            skuId,
            -25,
            BigDecimal.valueOf(15.00)
        );
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(mockMetadata()));

        var publisher = new EventPublisher(producer, objectMapper);
        publisher.publish(payload);

        verify(producer).send(any(ProducerRecord.class));
    }

    @Test
    void testFlushesAndClosesProducer() {
        var publisher = new EventPublisher(producer, objectMapper);
        publisher.close();

        verify(producer).flush();
        verify(producer).close();
    }

    @Test
    void testPublishesMultipleEventsSequentially() {
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(mockMetadata()));

        var publisher = new EventPublisher(producer, objectMapper);

        publisher.publish(createPayload());
        publisher.publish(new EventPayload(
            2L,
            "SALE",
            skuId + "-B",
            -5,
            BigDecimal.valueOf(20.00)
        ));

        verify(producer, times(2))
            .send(any(ProducerRecord.class));
    }

    @Test
    void testPublishesEventWithZeroQuantity() {
        var payload = new EventPayload(
            4L,
            "ADJUSTMENT",
            skuId,
            0,
            BigDecimal.ZERO
        );
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(mockMetadata()));

        var publisher = new EventPublisher(producer, objectMapper);
        publisher.publish(payload);

        verify(producer).send(any(ProducerRecord.class));
    }

    @Test
    void testPublishesEventWithLargeValues() {
        var payload = new EventPayload(
            Long.MAX_VALUE,
            "RECEIPT",
            skuId,
            Integer.MAX_VALUE,
            new BigDecimal("999999.99")
        );
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(mockMetadata()));

        var publisher = new EventPublisher(producer, objectMapper);
        publisher.publish(payload);

        verify(producer).send(any(ProducerRecord.class));
    }

    private RecordMetadata mockMetadata() {
        return mock(RecordMetadata.class);
    }
}
