package com.sirwellington.target.db;

import java.sql.SQLException;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.sirwellington.alchemy.annotations.arguments.Required;

import static tech.sirwellington.alchemy.arguments.Arguments.checkThat;
import static tech.sirwellington.alchemy.arguments.assertions.Assertions.notNull;

/**
 * Loads and executes schema.sql to ensure tables exist.
 */
public final class SchemaMigration {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaMigration.class);

    private SchemaMigration() {}

    /**
     * Reads schema.sql from the classpath and executes it against the given data source.
     */
    public static void run(@Required DataSource dataSource) throws SQLException {
        checkThat(dataSource).is(notNull());

        var schemaSql = Resources.load("/schema.sql");
        if (schemaSql == null || schemaSql.isBlank()) {
            LOG.info("No schema.sql found, skipping migration.");
            return;
        }
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (var statement = connection.createStatement()) {
                statement.execute(schemaSql);
            }
        }
        catch (SQLException ex) {
            LOG.error("Schema migration failed: {}", ex.getMessage());
            throw ex;
        }
        LOG.info("Schema migration complete.");
    }
}
