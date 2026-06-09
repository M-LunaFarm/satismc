package kr.seungmin.satisskyfactory.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class MigrationService {
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS count FROM schema_version")) {
                if (rs.next() && rs.getInt("count") == 0) {
                    statement.executeUpdate("INSERT INTO schema_version(version) VALUES (1)");
                }
            }
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS factory_islands (
                      island_uuid TEXT PRIMARY KEY,
                      owner_uuid TEXT NOT NULL,
                      tier INTEGER NOT NULL DEFAULT 1,
                      research_points INTEGER NOT NULL DEFAULT 0,
                      reputation INTEGER NOT NULL DEFAULT 0,
                      maintenance_debt INTEGER NOT NULL DEFAULT 0,
                      maintenance_status TEXT NOT NULL DEFAULT 'NORMAL',
                      factory_score INTEGER NOT NULL DEFAULT 0,
                      last_maintenance_at INTEGER NOT NULL DEFAULT 0,
                      last_tick_at INTEGER NOT NULL DEFAULT 0,
                      emergency_contracts_used_today INTEGER NOT NULL DEFAULT 0,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS machines (
                      machine_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      owner_uuid TEXT NOT NULL,
                      type_id TEXT NOT NULL,
                      tier INTEGER NOT NULL,
                      world TEXT NOT NULL,
                      x INTEGER NOT NULL,
                      y INTEGER NOT NULL,
                      z INTEGER NOT NULL,
                      direction TEXT NOT NULL,
                      status TEXT NOT NULL,
                      input_inventory_id TEXT,
                      output_inventory_id TEXT,
                      power_network_id TEXT,
                      item_network_id TEXT,
                      linked_resource_node_id TEXT,
                      last_process_at INTEGER NOT NULL,
                      wear REAL NOT NULL DEFAULT 0,
                      config_json TEXT NOT NULL DEFAULT '{}',
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_machines_location ON machines(world, x, y, z)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_machines_island ON machines(island_uuid)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS virtual_inventories (
                      inventory_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      holder_type TEXT NOT NULL,
                      holder_id TEXT NOT NULL,
                      capacity INTEGER NOT NULL,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS virtual_inventory_items (
                      inventory_id TEXT NOT NULL,
                      item_id TEXT NOT NULL,
                      amount INTEGER NOT NULL,
                      PRIMARY KEY(inventory_id, item_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS resource_nodes (
                      node_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      node_type TEXT NOT NULL,
                      resource_id TEXT NOT NULL,
                      purity REAL NOT NULL,
                      remaining INTEGER NOT NULL,
                      max_remaining INTEGER NOT NULL,
                      regen_per_hour INTEGER NOT NULL,
                      required_machine_tier INTEGER NOT NULL,
                      world TEXT NOT NULL,
                      x INTEGER NOT NULL,
                      y INTEGER NOT NULL,
                      z INTEGER NOT NULL,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS power_networks (
                      network_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      generation_per_second REAL NOT NULL DEFAULT 0,
                      consumption_per_second REAL NOT NULL DEFAULT 0,
                      battery_stored REAL NOT NULL DEFAULT 0,
                      battery_capacity REAL NOT NULL DEFAULT 0,
                      power_ratio REAL NOT NULL DEFAULT 1,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS item_networks (
                      network_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      throughput_per_minute INTEGER NOT NULL,
                      buffer_inventory_id TEXT,
                      dirty INTEGER NOT NULL DEFAULT 0,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS machine_network_links (
                      machine_id TEXT NOT NULL,
                      network_id TEXT NOT NULL,
                      network_type TEXT NOT NULL,
                      PRIMARY KEY(machine_id, network_id, network_type)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS market_daily (
                      item_id TEXT NOT NULL,
                      date_key TEXT NOT NULL,
                      sold_amount INTEGER NOT NULL DEFAULT 0,
                      demand_factor REAL NOT NULL DEFAULT 1,
                      PRIMARY KEY(item_id, date_key)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS market_personal_daily (
                      island_uuid TEXT NOT NULL,
                      item_id TEXT NOT NULL,
                      date_key TEXT NOT NULL,
                      sold_amount INTEGER NOT NULL DEFAULT 0,
                      PRIMARY KEY(island_uuid, item_id, date_key)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS contracts (
                      contract_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      template_id TEXT NOT NULL,
                      contract_type TEXT NOT NULL,
                      tier INTEGER NOT NULL,
                      required_json TEXT NOT NULL,
                      progress_json TEXT NOT NULL,
                      rewards_json TEXT NOT NULL,
                      status TEXT NOT NULL,
                      expires_at INTEGER NOT NULL,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS island_unlocks (
                      island_uuid TEXT NOT NULL,
                      unlock_id TEXT NOT NULL,
                      unlocked_at INTEGER NOT NULL,
                      PRIMARY KEY(island_uuid, unlock_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ledger (
                      ledger_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      type TEXT NOT NULL,
                      amount INTEGER NOT NULL,
                      reason TEXT NOT NULL,
                      created_at INTEGER NOT NULL
                    )
                    """);
            applyIncrementalMigrations(connection, statement);
        }
    }

    private void applyIncrementalMigrations(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "factory_islands", "emergency_contracts_used_today",
                "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "machines", "power_network_id", "TEXT");
        addColumnIfMissing(connection, statement, "machines", "item_network_id", "TEXT");
        addColumnIfMissing(connection, statement, "machines", "linked_resource_node_id", "TEXT");
        addColumnIfMissing(connection, statement, "machines", "config_json", "TEXT NOT NULL DEFAULT '{}'");
        addColumnIfMissing(connection, statement, "machines", "wear", "REAL NOT NULL DEFAULT 0");
        statement.executeUpdate("UPDATE schema_version SET version = 2");
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String table, String column, String definition)
            throws SQLException {
        if (!hasColumn(connection, table, column)) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
