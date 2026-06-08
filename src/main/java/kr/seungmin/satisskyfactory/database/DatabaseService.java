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
                return Optional.of(mapIsland(rs));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load factory island", exception);
        }
    }

    public List<FactoryIsland> loadIslands() {
        List<FactoryIsland> islands = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM factory_islands");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                islands.add(mapIsland(rs));
            }
            return islands;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load factory islands", exception);
        }
    }

    private FactoryIsland mapIsland(ResultSet rs) throws SQLException {
        FactoryIsland island = new FactoryIsland(UUID.fromString(rs.getString("island_uuid")), UUID.fromString(rs.getString("owner_uuid")));
        island.tier(rs.getInt("tier"));
        island.researchPoints(rs.getLong("research_points"));
        island.reputation(rs.getLong("reputation"));
        island.maintenanceDebt(rs.getLong("maintenance_debt"));
        island.maintenanceStatus(MaintenanceStatus.valueOf(rs.getString("maintenance_status")));
        island.factoryScore(rs.getLong("factory_score"));
        island.lastMaintenanceAt(rs.getLong("last_maintenance_at"));
        island.lastTickAt(rs.getLong("last_tick_at"));
        island.emergencyContractsUsedToday(rs.getInt("emergency_contracts_used_today"));
        return island;
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
                machine.powerNetworkId(uuidOrNull(rs.getString("power_network_id")));
                machine.itemNetworkId(uuidOrNull(rs.getString("item_network_id")));
                machine.linkedResourceNodeId(uuidOrNull(rs.getString("linked_resource_node_id")));
                machine.selectedRecipeId(selectedRecipeId(rs.getString("config_json")));
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
                       input_inventory_id, output_inventory_id, power_network_id, item_network_id, linked_resource_node_id,
                       last_process_at, wear, config_json, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(machine_id) DO UPDATE SET status=excluded.status, input_inventory_id=excluded.input_inventory_id,
                       output_inventory_id=excluded.output_inventory_id, power_network_id=excluded.power_network_id,
                       item_network_id=excluded.item_network_id, linked_resource_node_id=excluded.linked_resource_node_id,
                       last_process_at=excluded.last_process_at, wear=excluded.wear, config_json=excluded.config_json, updated_at=excluded.updated_at
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
            statement.setString(14, stringOrNull(machine.powerNetworkId()));
            statement.setString(15, stringOrNull(machine.itemNetworkId()));
            statement.setString(16, stringOrNull(machine.linkedResourceNodeId()));
            statement.setLong(17, machine.lastProcessAt());
            statement.setDouble(18, machine.wear());
            statement.setString(19, machineConfigJson(machine));
            statement.setLong(20, now);
            statement.setLong(21, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save machine", exception);
        }
    }

    private String selectedRecipeId(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String key = "\"selectedRecipe\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = start + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaped) {
                value.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                String parsed = value.toString();
                return parsed.isBlank() ? null : parsed;
            } else {
                value.append(current);
            }
        }
        return null;
    }

    private String machineConfigJson(MachineInstance machine) {
        String selectedRecipe = machine.selectedRecipeId();
        if (selectedRecipe == null || selectedRecipe.isBlank()) {
            return "{}";
        }
        return "{\"selectedRecipe\":\"" + selectedRecipe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
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
                            new BlockKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                            rs.getLong("updated_at")
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

    public long marketDailySold(String itemId, String dateKey) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT sold_amount FROM market_daily WHERE item_id = ? AND date_key = ?")) {
            statement.setString(1, itemId);
            statement.setString(2, dateKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("sold_amount") : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read market daily sold amount", exception);
        }
    }

    public long marketPersonalSold(UUID islandUuid, String itemId, String dateKey) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT sold_amount FROM market_personal_daily
                     WHERE island_uuid = ? AND item_id = ? AND date_key = ?
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, itemId);
            statement.setString(3, dateKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("sold_amount") : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read personal market sold amount", exception);
        }
    }

    public void recordMarketSale(UUID islandUuid, String itemId, String dateKey, long amount, double demandFactor) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement daily = connection.prepareStatement("""
                    INSERT INTO market_daily(item_id, date_key, sold_amount, demand_factor)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(item_id, date_key) DO UPDATE SET
                      sold_amount = sold_amount + excluded.sold_amount,
                      demand_factor = excluded.demand_factor
                    """)) {
                daily.setString(1, itemId);
                daily.setString(2, dateKey);
                daily.setLong(3, amount);
                daily.setDouble(4, demandFactor);
                daily.executeUpdate();
            }
            try (PreparedStatement personal = connection.prepareStatement("""
                    INSERT INTO market_personal_daily(island_uuid, item_id, date_key, sold_amount)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(island_uuid, item_id, date_key) DO UPDATE SET
                      sold_amount = sold_amount + excluded.sold_amount
                    """)) {
                personal.setString(1, islandUuid.toString());
                personal.setString(2, itemId);
                personal.setString(3, dateKey);
                personal.setLong(4, amount);
                personal.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record market sale", exception);
        }
    }

    public List<StoredContract> loadContracts(UUID islandUuid, String status) {
        List<StoredContract> contracts = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM contracts WHERE island_uuid = ? AND status = ? ORDER BY created_at ASC
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, status);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    contracts.add(new StoredContract(
                            UUID.fromString(rs.getString("contract_id")),
                            islandUuid,
                            rs.getString("template_id"),
                            rs.getString("contract_type"),
                            rs.getInt("tier"),
                            rs.getString("required_json"),
                            rs.getString("progress_json"),
                            rs.getString("rewards_json"),
                            rs.getString("status"),
                            rs.getLong("expires_at")
                    ));
                }
            }
            return contracts;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load contracts", exception);
        }
    }

    public boolean hasContractForTemplate(UUID islandUuid, String templateId, String status) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM contracts WHERE island_uuid = ? AND template_id = ? AND status = ? LIMIT 1
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, templateId);
            statement.setString(3, status);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check contract", exception);
        }
    }

    public int countContracts(UUID islandUuid, String contractType, String status, long updatedSince) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) AS count FROM contracts
                     WHERE island_uuid = ? AND contract_type = ? AND status = ? AND updated_at >= ?
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, contractType);
            statement.setString(3, status);
            statement.setLong(4, updatedSince);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to count contracts", exception);
        }
    }

    public void saveContract(StoredContract contract) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO contracts(contract_id, island_uuid, template_id, contract_type, tier, required_json,
                       progress_json, rewards_json, status, expires_at, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(contract_id) DO UPDATE SET progress_json=excluded.progress_json,
                       status=excluded.status, updated_at=excluded.updated_at
                     """)) {
            statement.setString(1, contract.contractId().toString());
            statement.setString(2, contract.islandUuid().toString());
            statement.setString(3, contract.templateId());
            statement.setString(4, contract.contractType());
            statement.setInt(5, contract.tier());
            statement.setString(6, contract.requiredJson());
            statement.setString(7, contract.progressJson());
            statement.setString(8, contract.rewardsJson());
            statement.setString(9, contract.status());
            statement.setLong(10, contract.expiresAt());
            statement.setLong(11, now);
            statement.setLong(12, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save contract", exception);
        }
    }

    public void updateContractStatus(UUID contractId, String status, String progressJson) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE contracts SET status = ?, progress_json = ?, updated_at = ? WHERE contract_id = ?
                     """)) {
            statement.setString(1, status);
            statement.setString(2, progressJson);
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.setString(4, contractId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update contract", exception);
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

    public record StoredContract(
            UUID contractId,
            UUID islandUuid,
            String templateId,
            String contractType,
            int tier,
            String requiredJson,
            String progressJson,
            String rewardsJson,
            String status,
            long expiresAt
    ) {
    }
}
