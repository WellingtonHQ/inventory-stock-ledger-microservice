package com.sirwellington.target.producer;

import java.util.Map;

import com.sirwellington.target.EnvConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaProducerConfig {

    private KafkaProducerConfig() {}

    public static final String BOOTSTRAP_SERVERS = EnvConfig.get("KAFKA_BOOTSTRAP", "localhost:9092");
    public static final String TOPIC             = EnvConfig.get("KAFKA_TOPIC", "inventory-events");

    public static KafkaProducer<String, String> create() {
        var props = Map.<String, Object>of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS,
            "key.serializer", StringSerializer.class.getName(),
            "value.serializer", StringSerializer.class.getName()
        );
        return new KafkaProducer<>(props);
    }
}
