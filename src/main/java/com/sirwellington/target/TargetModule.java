package com.sirwellington.target;

import java.util.Objects;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.sirwellington.target.db.InventoryRepository;
import com.sirwellington.target.producer.EventPublisher;
import com.sirwellington.target.producer.KafkaProducerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import tech.sirwellington.alchemy.annotations.arguments.Required;

public class TargetModule extends AbstractModule {

    private final DataSource dataSource;

    @Inject
    public TargetModule(@Required DataSource dataSource) {
        Objects.requireNonNull(dataSource);
        this.dataSource = dataSource;
    }

    @Override
    protected void configure() {
        super.configure();
        bind(InventoryRepository.class).in(Singleton.class);
        bind(KafkaProducer.class).toInstance(KafkaProducerConfig.create());
        bind(DataSource.class).toInstance(dataSource);
    }

    @Provides
    @Singleton
    ObjectMapper provideJsonMapper() {
        return new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Provides
    DSLContext provideJooqDsl(DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @Provides
    @Singleton
    EventPublisher provideEventPublisher(
        KafkaProducer<String, String> producer,
        ObjectMapper objectMapper
    ) {
        return new EventPublisher(producer, objectMapper);
    }

}
