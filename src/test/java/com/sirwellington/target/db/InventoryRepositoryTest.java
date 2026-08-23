package com.sirwellington.target.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.sirwellington.target.db.InventoryRepository.GetLedgerHistoryQuery;
import com.sirwellington.target.db.InventoryRepository.InsertTransactionRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sirwellington.target.model.TransactionType;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import tech.sirwellington.alchemy.generator.DateGenerators;
import tech.sirwellington.alchemy.generator.TimeGenerators;

import javax.sql.DataSource;

import static java.time.temporal.ChronoUnit.*;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.sirwellington.alchemy.generator.AlchemyGenerator.one;
import static tech.sirwellington.alchemy.generator.TimeGenerators.futureInstants;

class InventoryRepositoryTest {

    private static EmbeddedPostgres embeddedPg;
    private static DataSource dataSource;

    private Connection connection;
    private InventoryRepository repository;

    @BeforeAll
    static void startDatabase() throws Exception {
        embeddedPg = EmbeddedPostgres.builder().start();
        dataSource = embeddedPg.getPostgresDatabase();
        SchemaMigration.run(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = dataSource.getConnection();
        TestDbUtils.truncateAll(connection);
        repository = new InventoryRepository(DSL.using(dataSource, SQLDialect.POSTGRES));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (embeddedPg != null) {
            embeddedPg.close();
        }
    }

    @Test
    void testInsertTransactionReturnsGeneratedIdAndTimestamp() {
        var request = new InsertTransactionRequest(
            TransactionType.RECEIPT,
            "SKU-001",
            100,
            BigDecimal.valueOf(5.50)
        );

        var response = repository.insertTransaction(request);

        assertThat(response.transactionId()).isGreaterThan(0);
        assertThat(response.transactionTimestamp()).isNotNull();
    }

    @Test
    void testInsertMultipleTransactionsReturnsUniqueIds() {
        var id1 = repository.insertTransaction(new InsertTransactionRequest(
            TransactionType.RECEIPT,
            "SKU-A",
            50,
            BigDecimal.valueOf(10.00)
        )).transactionId();

        var id2 = repository.insertTransaction(new InsertTransactionRequest(
            TransactionType.SALE,
            "SKU-B",
            -10,
            BigDecimal.valueOf(8.00)
        )).transactionId();

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void testInsertReceiptTransaction() {
        var response = repository.insertTransaction(new InsertTransactionRequest(
            TransactionType.RECEIPT,
            "SKU-REC",
            200,
            BigDecimal.valueOf(3.75)
        ));

        assertThat(response.transactionId()).isGreaterThan(0);
        assertThat(response.transactionTimestamp()).isNotNull();
    }

    @Test
    void testInsertAdjustmentTransaction() {
        var response = repository.insertTransaction(new InsertTransactionRequest(
            TransactionType.ADJUSTMENT,
            "SKU-ADJ",
            -25,
            BigDecimal.valueOf(12.00)
        ));

        assertThat(response.transactionId()).isGreaterThan(0);
    }

    @Test
    void testInsertSaleTransaction() {
        var response = repository.insertTransaction(new InsertTransactionRequest(
            TransactionType.SALE,
            "SKU-SALE",
            -5,
            BigDecimal.valueOf(25.00)
        ));

        assertThat(response.transactionId()).isGreaterThan(0);
    }

    @Test
    void testGetInventoryValueReturnsValueForExistingSku() throws Exception {
        try (var stmt = connection.createStatement()) {
            stmt.execute(
                "INSERT INTO sku_inventory_snapshots (sku_id, current_quantity, total_current_value) " +
                "VALUES ('SKU-EXIST', 500, 2500.00)"
            );
        }

        var result = repository.getInventoryValue(new InventoryRepository.GetInventoryValueRequest("SKU-EXIST"));

        assertThat(result).isPresent();
        assertThat(result.get().currentQuantity()).isEqualTo(500);
        assertThat(result.get().totalCurrentValue()).isEqualByComparingTo(BigDecimal.valueOf(2500.00));
    }

    @Test
    void testGetInventoryValueReturnsEmptyForMissingSku() {
        var result = repository.getInventoryValue(
            new InventoryRepository.GetInventoryValueRequest("SKU-MISSING")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void testGetLedgerHistoryReturnsTransactionsInRange() throws Exception {
        var now = Instant.now();
        try (var stmt = connection.createStatement()) {
            stmt.execute(
                "INSERT INTO inventory_transactions (sku_id, transaction_type, quantity_change, unit_cost, transaction_timestamp) VALUES " +
                "('SKU-HIST', 'RECEIPT', 100, 5.00, '" + now + "'), " +
                "('SKU-HIST', 'SALE', -20, 5.00, '" + now.plus(5, MINUTES) + "')"
            );
        }

        var result = repository.getLedgerHistory(new GetLedgerHistoryQuery(
            now.minus(1, DAYS), now.plus(1, DAYS)
        ));

        assertThat(result.transactions()).hasSize(2);
    }

    @Test
    void testGetLedgerHistoryReturnsEmptyWhenNoTransactionsInRange() {
        var future = Instant.now().plus(30, DAYS);
        var result = repository.getLedgerHistory(new GetLedgerHistoryQuery(
            future, future.plus(1, DAYS)
        ));

        assertThat(result.transactions()).isEmpty();
    }

    @Test
    void testGetLedgerHistoryOrdersByTimestampAscending() throws Exception {
        try (var stmt = connection.createStatement()) {
            stmt.execute(
                "INSERT INTO inventory_transactions (transaction_timestamp, sku_id, transaction_type, quantity_change, unit_cost) VALUES " +
                "(TIMESTAMP '2026-03-15 10:00:00', 'SKU-ORD', 'RECEIPT', 10, 5.00), " +
                "(TIMESTAMP '2026-03-10 08:00:00', 'SKU-ORD', 'RECEIPT', 20, 5.00)"
            );
        }

        var result = repository.getLedgerHistory(new GetLedgerHistoryQuery(
            Instant.parse("2026-03-01T00:00:00Z"),
            Instant.parse("2026-03-31T23:59:59Z")
        ));

        assertThat(result.transactions()).hasSize(2);
    }

    @Test
    void testGetLedgerHistoryFiltersByDateRange() throws Exception {
        try (var stmt = connection.createStatement()) {
            stmt.execute(
                "INSERT INTO inventory_transactions (transaction_timestamp, sku_id, transaction_type, quantity_change, unit_cost) VALUES " +
                "(TIMESTAMP '2026-01-15 10:00:00', 'SKU-FIL', 'RECEIPT', 10, 5.00), " +
                "(TIMESTAMP '2026-06-15 10:00:00', 'SKU-FIL', 'SALE', -5, 5.00)"
            );
        }

        var result = repository.getLedgerHistory(new GetLedgerHistoryQuery(
            Instant.parse("2026-04-01T00:00:00Z"),
            Instant.parse("2026-09-30T23:59:59Z")
        ));

        assertThat(result.transactions()).hasSize(1);
    }

    @Test
    void testTransactionRecordContainsCorrectFields() throws Exception {
        var response = repository.insertTransaction(new InsertTransactionRequest(
            TransactionType.RECEIPT, "SKU-FIELD", 30, BigDecimal.valueOf(7.25)
        ));

        try (var stmt = connection.createStatement()) {
            stmt.execute(
                "INSERT INTO sku_inventory_snapshots (sku_id, current_quantity, total_current_value) VALUES ('SKU-FIELD', 30, 217.50)"
            );
        }

        var historyQuery = repository.getLedgerHistory(new GetLedgerHistoryQuery(
            response.transactionTimestamp().minus(1, HOURS),
            response.transactionTimestamp().plus(1, HOURS)
        ));

        assertThat(historyQuery.transactions()).hasSize(1);
        var record = historyQuery.transactions().getFirst();
        assertThat(record.skuId()).isEqualTo("SKU-FIELD");
    }

    @Test
    void testInsertTransactionWithDecimalUnitCost() {
        var response = repository.insertTransaction(new InsertTransactionRequest(
            TransactionType.RECEIPT, "SKU-DEC", 15, new BigDecimal("42.8765")
        ));

        assertThat(response.transactionId()).isGreaterThan(0);
    }

    @Test
    void testInsertTransactionReturnsDatabaseGeneratedTotal() {
        var request = new InsertTransactionRequest(
            TransactionType.RECEIPT,
            "SKU-RND",
            10,
            new BigDecimal("99.9999")
        );
        var response = repository.insertTransaction(request);

        assertThat(response.unitCost())
            .isEqualByComparingTo(new BigDecimal("99.9999"));
        assertThat(response.totalAmountImpact())
            .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void testInsertTransactionRoundsGeneratedTotalToTwoDecimals() {
        var request = new InsertTransactionRequest(
            TransactionType.RECEIPT,
            "SKU-RND2",
            15,
            new BigDecimal("42.8765")
        );
        var response = repository.insertTransaction(request);
        assertThat(response.totalAmountImpact())
            .isEqualByComparingTo(new BigDecimal("643.15"));
    }

    @Test
    void testInsertTransactionRoundsUnitCostToFourDecimals() {
        var request = new InsertTransactionRequest(
            TransactionType.RECEIPT,
            "SKU-RND3",
            100,
            new BigDecimal("1.23456")
        );
        var response = repository.insertTransaction(request);

        assertThat(response.unitCost())
            .isEqualByComparingTo(new BigDecimal("1.2346"));
        assertThat(response.totalAmountImpact())
            .isEqualByComparingTo(new BigDecimal("123.46"));
    }

    @Test
    void testGetInventoryValueWithDecimalPrecision() throws Exception {
        try (var stmt = connection.createStatement()) {
            stmt.execute(
                "INSERT INTO sku_inventory_snapshots (sku_id, current_quantity, total_current_value) " +
                "VALUES ('SKU-PREC', 1234, 56789.01)"
            );
        }

        var result = repository.getInventoryValue(
            new InventoryRepository.GetInventoryValueRequest("SKU-PREC")
        );

        assertThat(result).isPresent();
    }

    @Test
    void testEmptyLedgerHistoryResponseIsNotNull() {
        var future = one(futureInstants());
        var repositoryResponse = new GetLedgerHistoryQuery(
            future,
            future.plus(1, DAYS)
        );
        var result = repository.getLedgerHistory(repositoryResponse);

        assertThat(result).isNotNull();
        assertThat(result.transactions()).isNotNull();
    }
}
