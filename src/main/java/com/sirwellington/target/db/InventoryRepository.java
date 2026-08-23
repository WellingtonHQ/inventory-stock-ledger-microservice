package com.sirwellington.target.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import com.sirwellington.target.model.TransactionType;
import com.sirwellington.target.rest.OperationFailedException;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryRepository {

    public record InsertTransactionRequest(
        TransactionType type,
        String skuId,
        int quantityChange,
        BigDecimal unitCost
    ) {}

    public record InsertTransactionResponse(
        long transactionId,
        Instant transactionTimestamp,
        BigDecimal unitCost,
        BigDecimal totalAmountImpact
    ) {}

    public record GetInventoryValueRequest(
        String skuId
    ) {}

    public record GetInventoryValueResponse(
        int currentQuantity,
        BigDecimal totalCurrentValue
    ) {}

    public record GetLedgerHistoryQuery(
        Instant startDate,
        Instant endDate
    ) {}

    public record TransactionRecord(
        long transactionId,
        Instant transactionTimestamp,
        String skuId,
        String transactionType,
        int quantityChange,
        BigDecimal unitCost,
        BigDecimal totalAmountImpact
    ) {}

    public record GetLedgerHistoryResponse(
        List<TransactionRecord> transactions
    ) {}

    private static final Table<?> INVENTORY_TRANSACTIONS = DSL.table(
        DSL.name("inventory_transactions")
    );

    private static final Field<Long> TRANSACTION_ID = DSL.field(
        "transaction_id",
        Long.class
    );
    private static final Field<Instant> TRANSACTION_TIMESTAMP = DSL.field(
        "transaction_timestamp",
        Instant.class
    );
    private static final Field<String> SKU_ID = DSL.field(
        "sku_id",
        String.class
    );
    private static final Field<String> TRANSACTION_TYPE = DSL.field(
        "transaction_type",
        String.class
    );
    private static final Field<Integer> QUANTITY_CHANGE = DSL.field(
        "quantity_change",
        Integer.class
    );
    private static final Field<BigDecimal> UNIT_COST = DSL.field(
        "unit_cost",
        BigDecimal.class
    );
    private static final Field<BigDecimal> TOTAL_AMOUNT_IMPACT = DSL.field(
        "total_amount_impact",
        BigDecimal.class
    );

    private static final Table<?> SKU_INVENTORY_SNAPSHOTS = DSL.table(
        DSL.name("sku_inventory_snapshots")
    );

    private static final Field<Integer> CURRENT_QUANTITY = DSL.field(
        "current_quantity",
        Integer.class
    );
    private static final Field<BigDecimal> TOTAL_CURRENT_VALUE = DSL.field(
        "total_current_value",
        BigDecimal.class
    );

    private static final Logger LOG = LoggerFactory.getLogger(InventoryRepository.class);

    private final DSLContext dsl;

    public InventoryRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl);
    }

    public InsertTransactionResponse insertTransaction(InsertTransactionRequest request) {
        var now = Instant.now();

        try {
            var record = dsl.insertInto(
                INVENTORY_TRANSACTIONS,
                SKU_ID,
                TRANSACTION_TYPE,
                QUANTITY_CHANGE,
                UNIT_COST,
                TRANSACTION_TIMESTAMP
            ).values(
                request.skuId,
                request.type.name(),
                request.quantityChange,
                request.unitCost,
                now
            ).returning(TRANSACTION_ID, UNIT_COST, TOTAL_AMOUNT_IMPACT)
             .fetchOne();

            return new InsertTransactionResponse(
                record.get(TRANSACTION_ID),
                now,
                record.get(UNIT_COST),
                record.get(TOTAL_AMOUNT_IMPACT)
            );
        } catch (Exception ex) {
            var message = "Failed to insert transaction into 'inventory_transactions'";
            LOG.error(message, ex);
            throw new OperationFailedException(message, ex);
        }
    }

    public Optional<GetInventoryValueResponse> getInventoryValue(GetInventoryValueRequest request) {
        var result = dsl.select(CURRENT_QUANTITY, TOTAL_CURRENT_VALUE)
                        .from(SKU_INVENTORY_SNAPSHOTS)
                        .where(SKU_ID.eq(request.skuId()))
                        .fetchOptional();

        return result.map(record -> new GetInventoryValueResponse(
            record.get(CURRENT_QUANTITY),
            record.get(TOTAL_CURRENT_VALUE)
        ));
    }

    public GetLedgerHistoryResponse getLedgerHistory(GetLedgerHistoryQuery query) {
        var records = dsl.select(
                            TRANSACTION_ID,
                            TRANSACTION_TIMESTAMP,
                            SKU_ID,
                            TRANSACTION_TYPE,
                            QUANTITY_CHANGE,
                            UNIT_COST,
                            TOTAL_AMOUNT_IMPACT
                        )
                         .from(INVENTORY_TRANSACTIONS)
                         .where(TRANSACTION_TIMESTAMP.ge(query.startDate()))
                         .and(TRANSACTION_TIMESTAMP.le(query.endDate()))
                         .orderBy(TRANSACTION_TIMESTAMP.asc())
                         .fetch()
                         .map(record -> new TransactionRecord(
                             record.get(TRANSACTION_ID),
                             record.get(TRANSACTION_TIMESTAMP),
                             record.get(SKU_ID),
                             record.get(TRANSACTION_TYPE),
                             record.get(QUANTITY_CHANGE),
                             record.get(UNIT_COST),
                             record.get(TOTAL_AMOUNT_IMPACT)
                         ));

        return new GetLedgerHistoryResponse(records);
    }
}
