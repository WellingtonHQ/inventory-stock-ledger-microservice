package com.sirwellington.target.rest;

import java.sql.SQLException;

import com.google.inject.Guice;
import com.sirwellington.target.db.DatabaseConfig;
import com.sirwellington.target.db.SchemaMigration;
import com.sirwellington.target.TargetModule;
import com.sirwellington.target.producer.EventPublisher;
import io.javalin.Javalin;
import jdk.jfr.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestApplication {

    private static final Logger LOG = LoggerFactory.getLogger(RestApplication.class);
    private static final int PORT = Integer.parseInt(System.getProperty("port", "7070"));

    public static void run() {

        var database = DatabaseConfig.createDataSource();

        // Ensure the database pool is cleanly closed before shutdown.
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            LOG.info("Shutting down...");
            try {
                database.close();
            } catch (Exception e) {
                LOG.error("Error closing database", e);
            }
        }));

        try {
            SchemaMigration.run(database);
        } catch (SQLException e) {
            LOG.error("Schema migration failed", e);
            throw new RuntimeException("Schema migration failed", e);
        }

        var injector = Guice.createInjector(new TargetModule(database));

        // Flush and close the event publisher before shutdown.
        var eventPublisher = injector.getInstance(EventPublisher.class);
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            LOG.info("Closing event publisher");
            eventPublisher.close();
        }));
        var healthHandler = injector.getInstance(GetHealthHandler.class);
        var recordReceiptHandler = injector.getInstance(RecordReceiptHandler.class);
        var adjustCostHandler = injector.getInstance(AdjustCostHandler.class);
        var getCurrentValueHandler = injector.getInstance(GetCurrentValueHandler.class);
        var getLedgerHistoryHandler = injector.getInstance(GetLedgerHistoryHandler.class);

        var app = Javalin.create(config -> {
            config.routes.get("/health", healthHandler::handle);
            config.routes.post("/api/v1/transactions/receipt", recordReceiptHandler::handle);
            config.routes.put("/api/v1/inventory/{skuId}/cost-adjustment", adjustCostHandler::handle);
            config.routes.get("/api/v1/inventory/{skuId}/current-value", getCurrentValueHandler::handle);
            config.routes.get("/api/v1/ledger/history", getLedgerHistoryHandler::handle);
            config.concurrency.useVirtualThreads = true;
        });

        app.start(PORT);
        LOG.info("Listening on http://localhost:{}", PORT);
    }
}
