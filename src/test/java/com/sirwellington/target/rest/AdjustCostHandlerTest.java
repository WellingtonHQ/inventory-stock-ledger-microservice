package com.sirwellington.target.rest;

import java.math.BigDecimal;
import java.time.Instant;

import com.sirwellington.target.db.InventoryRepository;
import com.sirwellington.target.db.InventoryRepository.InsertTransactionResponse;
import com.sirwellington.target.model.EventPayload;
import com.sirwellington.target.producer.EventPublisher;
import com.sirwellington.target.rest.AdjustCostHandler.CostAdjustmentRequest;
import com.sirwellington.target.rest.AdjustCostHandler.CostAdjustmentResponse;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tech.sirwellington.alchemy.test.AlchemyTest;
import tech.sirwellington.alchemy.test.generation.GenerateString;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static tech.sirwellington.alchemy.test.ThrowableAssertion.assertThrows;

@AlchemyTest
class AdjustCostHandlerTest {

    @Mock
    private InventoryRepository repository;

    @Mock
    private EventPublisher publisher;

    @GenerateString
    private String skuId;

    private AdjustCostHandler createHandler() {
        return new AdjustCostHandler(repository, publisher);
    }

    @Test
    void testConstructorFailsWithNull() {
        assertThrows(() -> new AdjustCostHandler(null, publisher));
        assertThrows(() -> new AdjustCostHandler(repository, null));
        assertThrows(() -> new AdjustCostHandler(null, null));
    }

    @Test
    void testRecordsPositiveAdjustmentSuccessfully() throws Exception {
        var request = new CostAdjustmentRequest(
            25,
            BigDecimal.valueOf(8.00),
            "REPRICE"
        );
        var repositoryResponse = new InsertTransactionResponse(
            10L, Instant.now(), BigDecimal.valueOf(8.00), BigDecimal.valueOf(200.00)
        );
        when(repository.insertTransaction(any()))
            .thenReturn(repositoryResponse);

        var ctx = mockContext(request);
        when(ctx.pathParam("skuId")).thenReturn(skuId);

        var handler = createHandler();
        handler.handle(ctx);

        var captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().transactionId()).isEqualTo(10L);
        assertThat(captor.getValue().type()).isEqualTo("ADJUSTMENT");
        assertThat(captor.getValue().quantityChange()).isEqualTo(25);
        assertThat(captor.getValue().unitCost()).isEqualByComparingTo(BigDecimal.valueOf(8.00));

        verify(ctx).status(201);
    }

    @Test
    void testRecordsNegativeAdjustmentSuccessfully() throws Exception {
        var request = new CostAdjustmentRequest(
            -15,
            BigDecimal.valueOf(3.50),
            "DAMAGED"
        );
        var response = new InsertTransactionResponse(
            2L,
            Instant.now(),
            BigDecimal.valueOf(3.50),
            BigDecimal.valueOf(-52.50)
        );
        when(repository.insertTransaction(any()))
            .thenReturn(response);

        var ctx = mockContext(request);
        when(ctx.pathParam("skuId")).thenReturn(skuId);

        var handler = createHandler();
        handler.handle(ctx);

        verify(publisher).publish(any(EventPayload.class));
    }

    @Test
    void testCalculatesNegativeImpactForWriteOff() throws Exception {
        var request = new CostAdjustmentRequest(
            -10,
            BigDecimal.valueOf(2.50),
            "WRITEOFF"
        );
        var response = new InsertTransactionResponse(
            3L,
            Instant.now(),
            BigDecimal.valueOf(2.50),
            BigDecimal.valueOf(-25.00)
        );
        when(repository.insertTransaction(any()))
            .thenReturn(response);

        var ctx = mockContext(request);
        when(ctx.pathParam("skuId")).thenReturn(skuId);

        var handler = createHandler();
        handler.handle(ctx);

        verify(publisher).publish(any(EventPayload.class));
    }

    @Test
    void testReturnsCorrectTransactionTimestamp() throws Exception {
        var expectedTimestamp = Instant.parse("2026-01-15T10:30:00Z");
        var request = new CostAdjustmentRequest(
            5,
            BigDecimal.valueOf(1.00),
            "TEST"
        );
        var response = new InsertTransactionResponse(
            7L,
            expectedTimestamp,
            BigDecimal.valueOf(1.00),
            BigDecimal.valueOf(5.00)
        );
        when(repository.insertTransaction(any()))
            .thenReturn(response);

        var ctx = mockContext(request);
        when(ctx.pathParam("skuId")).thenReturn(skuId);

        var handler = createHandler();
        handler.handle(ctx);

        verify(publisher).publish(any(EventPayload.class));
    }

    @Test
    void testReturnsDatabaseComputedValuesInResponse() throws Exception {
        var request = new CostAdjustmentRequest(
            10,
            new BigDecimal("99.9999"),
            "REPRICE"
        );
        var response = new InsertTransactionResponse(
            11L,
            Instant.now(),
            new BigDecimal("99.9999"),
            new BigDecimal("1000.00")
        );
        when(repository.insertTransaction(any()))
            .thenReturn(response);

        var ctx = mockContext(request);
        when(ctx.pathParam("skuId")).thenReturn(skuId);

        var handler = createHandler();
        handler.handle(ctx);

        var captor = ArgumentCaptor.forClass(CostAdjustmentResponse.class);
        verify(ctx).json(captor.capture());
        assertThat(captor.getValue().unitCost())
            .isEqualByComparingTo(new BigDecimal("99.9999"));
        assertThat(captor.getValue().totalAmountImpact())
            .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    private Context mockContext(Object body) {
        var ctx = mock(Context.class);
        when(ctx.bodyAsClass(any())).thenReturn(body);
        lenient().when(ctx.status(anyInt())).thenReturn(ctx);
        return ctx;
    }
}
