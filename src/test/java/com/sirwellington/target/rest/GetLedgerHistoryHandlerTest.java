package com.sirwellington.target.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.sirwellington.target.db.InventoryRepository;
import com.sirwellington.target.db.InventoryRepository.GetLedgerHistoryResponse;
import com.sirwellington.target.db.InventoryRepository.TransactionRecord;
import com.sirwellington.target.rest.GetLedgerHistoryHandler.GetLedgerHistoryResponse.LedgerEntry;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tech.sirwellington.alchemy.test.AlchemyTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@AlchemyTest
class GetLedgerHistoryHandlerTest {

    @Mock
    private InventoryRepository repository;

    private GetLedgerHistoryHandler createHandler() {
        return new GetLedgerHistoryHandler(repository);
    }

    @Test
    void testReturnsEntriesForValidDateRange() throws Exception {
        var start = Instant.parse("2026-01-01T00:00:00Z");
        var end = Instant.parse("2026-01-31T23:59:59Z");

        var record = new TransactionRecord(
            1L,
            start.plus(5, ChronoUnit.DAYS),
            "SKU-001", "RECEIPT",
            50,
            new BigDecimal("10.00"),
            new BigDecimal("500.00")
        );
        var repositoryResponse = new GetLedgerHistoryResponse(List.of(record));
        when(repository.getLedgerHistory(any()))
            .thenReturn(repositoryResponse);

        var ctx = mockContext(start.toString(), end.toString());
        var handler = createHandler();
        handler.handle(ctx);

        var response = captureJsonResponse(ctx);
        var entries = response.entries();
        assertThat(entries).hasSize(1);

        assertEntryMatchesRecord(entries.getFirst(), record);
    }

    @Test
    void testReturnsMultipleEntriesWhenAvailable() throws Exception {
        var start = Instant.parse("2026-01-01T00:00:00Z");
        var end = Instant.parse("2026-01-31T23:59:59Z");

        var records = List.of(
            new TransactionRecord(
                1L,
                start.plus(1, ChronoUnit.DAYS),
                "SKU-A",
                "RECEIPT",
                100,
                new BigDecimal("5.00"),
                new BigDecimal("500.00")
            ),
            new TransactionRecord(
                2L,
                start.plus(2, ChronoUnit.DAYS),
                "SKU-B",
                "SALE",
                -10,
                new BigDecimal("8.00"),
                new BigDecimal("-80.00")
            ),
            new TransactionRecord(
                3L,
                start.plus(3, ChronoUnit.DAYS),
                "SKU-A",
                "ADJUSTMENT",
                5,
                new BigDecimal("5.00"),
                new BigDecimal("25.00")
            )
        );
        var repositoryResponse = new GetLedgerHistoryResponse(records);
        when(repository.getLedgerHistory(any()))
            .thenReturn(repositoryResponse);

        var ctx = mockContext(start.toString(), end.toString());

        var handler = createHandler();
        handler.handle(ctx);

        var response = captureJsonResponse(ctx);
        var entries = response.entries();
        assertThat(entries).hasSize(records.size());

        for (var i = 0; i < records.size(); i++) {
            assertEntryMatchesRecord(entries.get(i), records.get(i));
        }
    }

    @Test
    void testReturnsEmptyListWhenNoTransactionsInRange() throws Exception {
        var start = Instant.parse("2026-06-01T00:00:00Z");
        var end = Instant.parse("2026-06-30T23:59:59Z");
        var repositoryResponse = new GetLedgerHistoryResponse(List.of());
        when(repository.getLedgerHistory(any()))
            .thenReturn(repositoryResponse);

        var ctx = mockContext(start.toString(), end.toString());

        var handler = createHandler();
        handler.handle(ctx);

        var response = captureJsonResponse(ctx);
        assertThat(response.entries()).isEmpty();
    }

    @Test
    void testReturns400WhenStartDateMissing() throws Exception {
        var ctx = mockContext(null, "2026-01-31T23:59:59Z");
        var handler = createHandler();
        handler.handle(ctx);

        verify(ctx).status(400);
    }

    @Test
    void testReturns400WhenEndDateMissing() throws Exception {
        var ctx = mockContext("2026-01-01T00:00:00Z", null);

        var handler = createHandler();
        handler.handle(ctx);

        verify(ctx).status(400);
    }

    @Test
    void testReturns400WhenBothDatesMissing() throws Exception {
        var ctx = mockContext(null, null);

        var handler = createHandler();
        handler.handle(ctx);

        verify(ctx).status(400);
    }

    @Test
    void testMapsTransactionRecordToLedgerEntry() throws Exception {
        var timestamp = Instant.parse("2026-03-15T12:00:00Z");
        var record = new TransactionRecord(
            99L,
            timestamp,
            "SKU-MAP",
            "RECEIPT",
            75,
            new BigDecimal("12.50"),
            new BigDecimal("937.50")
        );
        var repositoryResponse = new GetLedgerHistoryResponse(List.of(record));
        when(repository.getLedgerHistory(any())).thenReturn(repositoryResponse);

        var ctx = mockContext("2026-03-01T00:00:00Z", "2026-03-31T23:59:59Z");

        var handler = createHandler();
        handler.handle(ctx);

        var response = captureJsonResponse(ctx);
        var entries = response.entries();
        assertThat(entries).hasSize(1);

        assertEntryMatchesRecord(entries.getFirst(), record);
    }

    void assertEntryMatchesRecord(
        LedgerEntry entry,
        TransactionRecord record
    ) {
        assertThat(entry.transactionId()).isEqualTo(record.transactionId());
        assertThat(entry.transactionTimestamp()).isEqualTo(record.transactionTimestamp());
        assertThat(entry.skuId()).isEqualTo(record.skuId());
        assertThat(entry.transactionType()).isEqualTo(record.transactionType());
        assertThat(entry.quantityChange()).isEqualTo(record.quantityChange());
        assertThat(entry.unitCost()).isEqualByComparingTo(record.unitCost());
        assertThat(entry.totalAmountImpact()).isEqualByComparingTo(record.totalAmountImpact());
    }

    GetLedgerHistoryHandler.GetLedgerHistoryResponse captureJsonResponse(Context ctx) {
        var captor = ArgumentCaptor.forClass(GetLedgerHistoryHandler.GetLedgerHistoryResponse.class);
        verify(ctx).json(captor.capture());
        return captor.getValue();
    }

    Context mockContext(String startDate, String endDate) {
        var ctx = mock(Context.class);
        when(ctx.queryParam("startDate")).thenReturn(startDate);
        when(ctx.queryParam("endDate")).thenReturn(endDate);
        lenient().when(ctx.status(anyInt())).thenReturn(ctx);
        return ctx;
    }
}
