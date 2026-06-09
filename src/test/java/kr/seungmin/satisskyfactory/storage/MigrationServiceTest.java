package kr.seungmin.satisskyfactory.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsGoalSchemaAndCanRunAgain() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("migration.db"))) {
            MigrationService migrations = new MigrationService();
            migrations.migrate(connection);
            migrations.migrate(connection);

            Set<String> tables = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
            }
            assertTrue(tables.containsAll(Set.of(
                    "factory_islands",
                    "machines",
                    "virtual_inventories",
                    "virtual_inventory_items",
                    "resource_nodes",
                    "power_networks",
                    "item_networks",
                    "machine_network_links",
                    "market_daily",
                    "market_personal_daily",
                    "contracts",
                    "island_unlocks",
                    "ledger",
                    "schema_version"
            )));
            try (Statement statement = connection.createStatement();
                 ResultSet version = statement.executeQuery("SELECT version FROM schema_version")) {
                assertTrue(version.next());
                assertEquals(2, version.getInt("version"));
            }
            assertTrue(columnNames(connection, "machines").containsAll(Set.of(
                    "power_network_id",
                    "item_network_id",
                    "linked_resource_node_id",
                    "config_json",
                    "wear"
            )));
        }
    }

    private Set<String> columnNames(Connection connection, String table) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }
}
