package com.sirwellington.target.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class TestDbUtils {

    private TestDbUtils() {}

    public static void truncateAll(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("TRUNCATE TABLE inventory_transactions RESTART IDENTITY");
            stmt.execute("DELETE FROM sku_inventory_snapshots");
        }
    }
}
