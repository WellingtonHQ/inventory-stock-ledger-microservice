package com.sirwellington.target.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sirwellington.target.db.InventoryRepository;
import com.sirwellington.target.model.EventPayload;
import com.sirwellington.target.model.TransactionType;
import com.sirwellington.target.producer.EventPublisher;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.sirwellington.alchemy.annotations.arguments.Required;
import tech.sirwellington.alchemy.arguments.assertions.NumberAssertions;

import static tech.sirwellington.alchemy.arguments.Arguments.checkThat;
import static tech.sirwellington.alchemy.arguments.assertions.StringAssertions.nonEmptyString;

@Singleton
public class RecordReceiptHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RecordReceiptHandler.class);
    private final InventoryRepository repository;
    private final EventPublisher publisher;

    @Inject
    public RecordReceiptHandler(
        @Required InventoryRepository repository,
        @Required EventPublisher publisher
    ) {
        Objects.requireNonNull(repository);
        Objects.requireNonNull(publisher);
        this.repository = repository;
        this.publisher = publisher;
    }

    public void handle(@Required Context ctx) throws Exception {
        var request = ctx.bodyAsClass(RecordReceiptRequest.class);
        checkThat(request.skuId)
            .usingMessage("skuId is required")
            .isA(nonEmptyString());
        checkThat(request.quantity)
            .usingMessage("quantity must be greater than 0")
            .isA(NumberAssertions.greaterThan(0));

        var response = repository.insertTransaction(new InventoryRepository.InsertTransactionRequest(
            TransactionType.RECEIPT,
            request.skuId(),
            request.quantity(),
            request.unitCost()
        ));

        var eventPayload = new EventPayload(
            response.transactionId(),
            TransactionType.RECEIPT.name(),
            request.skuId(),
            request.quantity(),
            response.unitCost()
        );

        publisher.publish(eventPayload);

        LOG.info("Receipt recorded: transactionId={}, sku={}", response.transactionId(), request.skuId());

        ctx.status(201).json(new RecordReceiptResponse(
            response.transactionId(),
            response.transactionTimestamp(),
            request.skuId(),
            TransactionType.RECEIPT.name(),
            request.quantity(),
            response.unitCost(),
            response.totalAmountImpact()
        ));
    }

    public record RecordReceiptRequest(
        String skuId,
        int quantity,
        BigDecimal unitCost
    ) {}

    public record RecordReceiptResponse(
        long transactionId,
        Instant transactionTimestamp,
        String skuId,
        String transactionType,
        int quantityChange,
        BigDecimal unitCost,
        BigDecimal totalAmountImpact
    ) {}

}
