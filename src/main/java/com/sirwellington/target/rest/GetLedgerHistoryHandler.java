package com.sirwellington.target.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sirwellington.target.db.InventoryRepository;
import com.sirwellington.target.db.InventoryRepository.GetLedgerHistoryQuery;
import com.sirwellington.target.rest.GetLedgerHistoryHandler.GetLedgerHistoryResponse.LedgerEntry;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.sirwellington.alchemy.annotations.arguments.Required;

@Singleton
public class GetLedgerHistoryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GetLedgerHistoryHandler.class);

    private final InventoryRepository repository;

    @Inject
    public GetLedgerHistoryHandler(@Required InventoryRepository repository) {
        Objects.requireNonNull(repository);
        this.repository = repository;
    }

    public void handle(Context ctx) throws Exception {
        var startDateStr = ctx.queryParam("startDate");
        var endDateStr = ctx.queryParam("endDate");

        if (startDateStr == null || endDateStr == null) {
            ctx.status(400)
               .json(Map.of(
                   "error", "Both 'startDate' and 'endDate' query parameters are required"
               ));
            return;
        }

        // TODO: Handle exceptions from parsing errors
        var startDate = Instant.parse(startDateStr);
        var endDate = Instant.parse(endDateStr);

        var result = repository.getLedgerHistory(new GetLedgerHistoryQuery(
            startDate,
            endDate
        ));

        var entries = result.transactions()
                            .stream()
                            .map(t -> new LedgerEntry(
                                t.transactionId(),
                                t.transactionTimestamp(),
                                t.skuId(),
                                t.transactionType(),
                                t.quantityChange(),
                                t.unitCost(),
                                t.totalAmountImpact()
                            ))
                            .toList();

        LOG.info(
            "Ledger history query: start={}, end={}, count={}",
            startDate, endDate, entries.size()
        );

        ctx.json(new GetLedgerHistoryResponse(entries));
    }


    public record GetLedgerHistoryResponse(
        List<LedgerEntry> entries
    ) {
        public record LedgerEntry(
            long transactionId,
            Instant transactionTimestamp,
            String skuId,
            String transactionType,
            int quantityChange,
            BigDecimal unitCost,
            BigDecimal totalAmountImpact
        ) {}
    }
}
