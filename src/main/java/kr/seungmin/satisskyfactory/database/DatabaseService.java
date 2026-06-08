package kr.seungmin.satisskyfactory.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DatabaseService {
    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() {
        File database = new File(plugin.getDataFolder(), "data.db");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + database.getAbsolutePath());
        config.setMaximumPoolSize(4);
        config.setPoolName("SatisSkyFactory");
        dataSource = new HikariDataSource(config);
        migrate();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    private void migrate() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
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
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to migrate SQLite database", exception);
        }
    }

    public Optional<FactoryIsland> findIsland(UUID islandUuid) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM factory_islands WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                FactoryIsland island = new FactoryIsland(islandUuid, UUID.fromString(rs.getString("owner_uuid")));
                island.tier(rs.getInt("tier"));
                island.researchPoints(rs.getLong("research_points"));
                island.reputation(rs.getLong("reputation"));
                island.maintenanceDebt(rs.getLong("maintenance_debt"));
                island.maintenanceStatus(MaintenanceStatus.valueOf(rs.getString("maintenance_status")));
                island.factoryScore(rs.getLong("factory_score"));
                island.lastMaintenanceAt(rs.getLong("last_maintenance_at"));
                island.lastTickAt(rs.getLong("last_tick_at"));
                island.emergencyContractsUsedToday(rs.getInt("emergency_contracts_used_today"));
                return Optional.of(island);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load factory island", exception);
        }
    }

    public void saveIsland(FactoryIsland island) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO factory_islands(island_uuid, owner_uuid, tier, research_points, reputation, maintenance_debt,
                       maintenance_status, factory_score, last_maintenance_at, last_tick_at, emergency_contracts_used_today, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(island_uuid) DO UPDATE SET owner_uuid=excluded.owner_uuid, tier=excluded.tier,
                       research_points=excluded.research_points, reputation=excluded.reputation,
                       maintenance_debt=excluded.maintenance_debt, maintenance_status=excluded.maintenance_status,
                       factory_score=excluded.factory_score, last_maintenance_at=excluded.last_maintenance_at,
                       last_tick_at=excluded.last_tick_at, emergency_contracts_used_today=excluded.emergency_contracts_used_today,
                       updated_at=excluded.updated_at
                     """)) {
            statement.setString(1, island.islandUuid().toString());
            statement.setString(2, island.ownerUuid().toString());
            statement.setInt(3, island.tier());
            statement.setLong(4, island.researchPoints());
            statement.setLong(5, island.reputation());
            statement.setLong(6, island.maintenanceDebt());
            statement.setString(7, island.maintenanceStatus().name());
            statement.setLong(8, island.factoryScore());
            statement.setLong(9, island.lastMaintenanceAt());
            statement.setLong(10, island.lastTickAt());
            statement.setInt(11, island.emergencyContractsUsedToday());
            statement.setLong(12, now);
            statement.setLong(13, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save factory island", exception);
        }
    }

    public List<MachineInstance> loadMachines() {
        List<MachineInstance> machines = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM machines");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                MachineInstance machine = new MachineInstance(
                        UUID.fromString(rs.getString("machine_id")),
                        UUID.fromString(rs.getString("island_uuid")),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("type_id"),
                        rs.getInt("tier"),
                        new BlockKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                );
                machine.direction(BlockFace.valueOf(rs.getString("direction")));
                machine.status(MachineStatus.valueOf(rs.getString("status")));
                machine.inputInventoryId(uuidOrNull(rs.getString("input_inventory_id")));
                machine.outputInventoryId(uuidOrNull(rs.getString("output_inventory_id")));
                machine.linkedResourceNodeId(uuidOrNull(rs.getString("linked_resource_node_id")));
                machine.lastProcessAt(rs.getLong("last_process_at"));
                machine.wear(rs.getDouble("wear"));
                machines.add(machine);
            }
            return machines;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load machines", exception);
        }
    }

    public void saveMachine(MachineInstance machine) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO machines(machine_id, island_uuid, owner_uuid, type_id, tier, world, x, y, z, direction, status,
                       input_inventory_id, output_inventory_id, linked_resource_node_id, last_process_at, wear, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(machine_id) DO UPDATE SET status=excluded.status, input_inventory_id=excluded.input_inventory_id,
                       output_inventory_id=excluded.output_inventory_id, linked_resource_node_id=excluded.linked_resource_node_id,
                       last_process_at=excluded.last_process_at, wear=excluded.wear, updated_at=excluded.updated_at
                     """)) {
            statement.setString(1, machine.machineId().toString());
            statement.setString(2, machine.islandUuid().toString());
            statement.setString(3, machine.ownerUuid().toString());
            statement.setString(4, machine.typeId());
            statement.setInt(5, machine.tier());
            statement.setString(6, machine.location().world());
            statement.setInt(7, machine.location().x());
            statement.setInt(8, machine.location().y());
            statement.setInt(9, machine.location().z());
            statement.setString(10, machine.direction().name());
            statement.setString(11, machine.status().name());
            statement.setString(12, stringOrNull(machine.inputInventoryId()));
            statement.setString(13, stringOrNull(machine.outputInventoryId()));
            statement.setString(14, stringOrNull(machine.linkedResourceNodeId()));
            statement.setLong(15, machine.lastProcessAt());
            statement.setDouble(16, machine.wear());
            statement.setLong(17, now);
            statement.setLong(18, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save machine", exception);
        }
    }

    public void deleteMachine(UUID machineId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM machines WHERE machine_id = ?")) {
            statement.setString(1, machineId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete machine", exception);
        }
    }

    public void saveInventory(VirtualInventory inventory) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement inv = connection.prepareStatement("""
                    INSERT INTO virtual_inventories(inventory_id, island_uuid, holder_type, holder_id, capacity, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(inventory_id) DO UPDATE SET capacity=excluded.capacity, updated_at=excluded.updated_at
                    """)) {
                inv.setString(1, inventory.inventoryId().toString());
                inv.setString(2, inventory.islandUuid().toString());
                inv.setString(3, inventory.holderType());
                inv.setString(4, inventory.holderId());
                inv.setInt(5, inventory.capacity());
                inv.setLong(6, now);
                inv.setLong(7, now);
                inv.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM virtual_inventory_items WHERE inventory_id = ?")) {
                delete.setString(1, inventory.inventoryId().toString());
                delete.executeUpdate();
            }
            try (PreparedStatement item = connection.prepareStatement("INSERT INTO virtual_inventory_items(inventory_id, item_id, amount) VALUES(?, ?, ?)")) {
                for (var entry : inventory.items().entrySet()) {
                    item.setString(1, inventory.inventoryId().toString());
                    item.setString(2, entry.getKey());
                    item.setLong(3, entry.getValue());
                    item.addBatch();
                }
                item.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save inventory", exception);
        }
    }

    public Optional<VirtualInventory> loadInventory(UUID inventoryId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM virtual_inventories WHERE inventory_id = ?")) {
            statement.setString(1, inventoryId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                VirtualInventory inventory = new VirtualInventory(
                        inventoryId,
                        UUID.fromString(rs.getString("island_uuid")),
                        rs.getString("holder_type"),
                        rs.getString("holder_id"),
                        rs.getInt("capacity")
                );
                loadInventoryItems(connection, inventory);
                return Optional.of(inventory);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load inventory", exception);
        }
    }

    public Optional<VirtualInventory> findInventoryByHolder(UUID islandUuid, String holderType, String holderId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT inventory_id FROM virtual_inventories
                     WHERE island_uuid = ? AND holder_type = ? AND holder_id = ?
                     LIMIT 1
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, holderType);
            statement.setString(3, holderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return loadInventory(UUID.fromString(rs.getString("inventory_id")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find inventory", exception);
        }
    }

    public List<ResourceNode> loadNodes(UUID islandUuid) {
        List<ResourceNode> nodes = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM resource_nodes WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    nodes.add(new ResourceNode(
                            UUID.fromString(rs.getString("node_id")),
                            islandUuid,
                            rs.getString("node_type"),
                            rs.getString("resource_id"),
                            rs.getDouble("purity"),
                            rs.getLong("remaining"),
                            rs.getLong("max_remaining"),
                            rs.getLong("regen_per_hour"),
                            rs.getInt("required_machine_tier"),
                            new BlockKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                    ));
                }
            }
            return nodes;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load resource nodes", exception);
        }
    }

    public void saveNode(ResourceNode node) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO resource_nodes(node_id, island_uuid, node_type, resource_id, purity, remaining, max_remaining,
                       regen_per_hour, required_machine_tier, world, x, y, z, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(node_id) DO UPDATE SET remaining=excluded.remaining, updated_at=excluded.updated_at
                     """)) {
            statement.setString(1, node.nodeId().toString());
            statement.setString(2, node.islandUuid().toString());
            statement.setString(3, node.nodeType());
            statement.setString(4, node.resourceId());
            statement.setDouble(5, node.purity());
            statement.setLong(6, node.remaining());
            statement.setLong(7, node.maxRemaining());
            statement.setLong(8, node.regenPerHour());
            statement.setInt(9, node.requiredMachineTier());
            statement.setString(10, node.location().world());
            statement.setInt(11, node.location().x());
            statement.setInt(12, node.location().y());
            statement.setInt(13, node.location().z());
            statement.setLong(14, now);
            statement.setLong(15, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save resource node", exception);
        }
    }

    public void addLedger(UUID islandUuid, String type, long amount, String reason) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO ledger(ledger_id, island_uuid, type, amount, reason, created_at) VALUES(?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, type);
            statement.setLong(4, amount);
            statement.setString(5, reason);
            statement.setLong(6, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to write ledger", exception);
        }
    }

    public Set<String> loadUnlocks(UUID islandUuid) {
        Set<String> unlocks = new HashSet<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT unlock_id FROM island_unlocks WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    unlocks.add(rs.getString("unlock_id"));
                }
            }
            return unlocks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load unlocks", exception);
        }
    }

    public void saveUnlock(UUID islandUuid, String unlockId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR IGNORE INTO island_unlocks(island_uuid, unlock_id, unlocked_at)
                     VALUES(?, ?, ?)
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, unlockId);
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save unlock", exception);
        }
    }

    private void loadInventoryItems(Connection connection, VirtualInventory inventory) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT item_id, amount FROM virtual_inventory_items WHERE inventory_id = ?")) {
            statement.setString(1, inventory.inventoryId().toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    inventory.set(rs.getString("item_id"), rs.getLong("amount"));
                }
            }
        }
    }

    private UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private String stringOrNull(UUID value) {
        return value == null ? null : value.toString();
    }
}
