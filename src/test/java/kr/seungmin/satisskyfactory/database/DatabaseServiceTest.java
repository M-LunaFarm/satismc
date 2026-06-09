package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.ItemNetwork;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.model.PowerNetwork;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void migrationCreatesExpectedTablesAndVersion() throws Exception {
        try (DatabaseHandle handle = openDatabase()) {
            try (Connection connection = handle.database().connection();
                 Statement statement = connection.createStatement();
                 ResultSet tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
                Set<String> tableNames = new java.util.HashSet<>();
                while (tables.next()) {
                    tableNames.add(tables.getString("name"));
                }
                assertTrue(tableNames.containsAll(Set.of(
                        "factory_islands",
                        "machines",
                        "virtual_inventories",
                        "virtual_inventory_items",
                        "resource_nodes",
                        "contracts",
                        "island_unlocks",
                        "market_daily",
                        "market_personal_daily",
                        "power_networks",
                        "item_networks",
                        "machine_network_links",
                        "ledger",
                        "schema_version"
                )));
            }
            try (Connection connection = handle.database().connection();
                 Statement statement = connection.createStatement();
                 ResultSet version = statement.executeQuery("SELECT version FROM schema_version")) {
                assertTrue(version.next());
                assertEquals(2, version.getInt("version"));
            }
        }
    }

    @Test
    void customSqliteFileNameControlsDatabasePath() {
        File dataFolder = tempDir.resolve("custom-db").toFile();
        try (DatabaseHandle ignored = openDatabase(dataFolder, "custom.sqlite")) {
            assertTrue(new File(dataFolder, "custom.sqlite").isFile());
            assertFalse(new File(dataFolder, "data.db").exists());
        }
    }

    @Test
    void islandInventoryMachineNodeAndUnlocksSurviveReopen() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID inputInventoryId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        UUID outputInventoryId = UUID.fromString("00000000-0000-0000-0000-000000000104");
        UUID machineId = UUID.fromString("00000000-0000-0000-0000-000000000105");
        UUID nodeId = UUID.fromString("00000000-0000-0000-0000-000000000106");
        File dataFolder = tempDir.resolve("db").toFile();

        try (DatabaseHandle handle = openDatabase(dataFolder)) {
            DatabaseService database = handle.database();
            FactoryIsland island = new FactoryIsland(islandUuid, ownerUuid);
            island.tier(2);
            island.researchPoints(45);
            island.reputation(12);
            island.maintenanceDebt(300);
            island.maintenanceStatus(MaintenanceStatus.LIMITED);
            island.factoryScore(88);
            island.lastMaintenanceAt(1000);
            island.lastTickAt(2000);
            island.createdAt(500);
            island.emergencyContractsUsedToday(1);
            database.saveIsland(island);

            VirtualInventory input = new VirtualInventory(inputInventoryId, islandUuid, "MACHINE_INPUT", machineId.toString(), 64);
            input.add("wheat", 16);
            database.saveInventory(input);
            VirtualInventory output = new VirtualInventory(outputInventoryId, islandUuid, "MACHINE_OUTPUT", machineId.toString(), 128);
            output.add("flour", 7);
            database.saveInventory(output);

            MachineInstance machine = new MachineInstance(machineId, islandUuid, ownerUuid, "grinder_t1", 1, new BlockKey("world", 10, 64, 12));
            machine.direction(BlockFace.EAST);
            machine.status(MachineStatus.ACTIVE);
            machine.inputInventoryId(inputInventoryId);
            machine.outputInventoryId(outputInventoryId);
            machine.selectedRecipeId("grind_wheat");
            machine.lastProcessAt(3000);
            machine.wear(1.25);
            database.saveMachine(machine);

            ResourceNode node = new ResourceNode(nodeId, islandUuid, "MINERAL", "iron_ore", 0.75, 500, 1000, 120, 1,
                    new BlockKey("world", 20, 64, 20), 4000);
            database.saveNode(node);

            database.saveUnlock(islandUuid, "tier_2");
            database.recordMarketSale(islandUuid, "flour", "2026-06-08", 12, 0.8);
            database.saveContract(new DatabaseService.StoredContract(
                    UUID.fromString("00000000-0000-0000-0000-000000000107"),
                    islandUuid,
                    "bread_supply",
                    "DAILY",
                    1,
                    "{\"bread_box\":8}",
                    "{}",
                    "{\"money\":200}",
                    "ACTIVE",
                    5000
            ));
        }

        try (DatabaseHandle handle = openDatabase(dataFolder)) {
            DatabaseService database = handle.database();
            FactoryIsland island = database.findIsland(islandUuid).orElseThrow();
            assertEquals(2, island.tier());
            assertEquals(45, island.researchPoints());
            assertEquals(12, island.reputation());
            assertEquals(300, island.maintenanceDebt());
            assertEquals(MaintenanceStatus.LIMITED, island.maintenanceStatus());
            assertEquals(88, island.factoryScore());
            assertEquals(500, island.createdAt());
            assertEquals(1, island.emergencyContractsUsedToday());

            VirtualInventory input = database.loadInventory(inputInventoryId).orElseThrow();
            assertEquals(16, input.amount("wheat"));
            VirtualInventory output = database.findInventoryByHolder(islandUuid, "MACHINE_OUTPUT", machineId.toString()).orElseThrow();
            assertEquals(outputInventoryId, output.inventoryId());
            assertEquals(7, output.amount("flour"));

            MachineInstance machine = database.loadMachines().stream()
                    .filter(candidate -> candidate.machineId().equals(machineId))
                    .findFirst()
                    .orElseThrow();
            assertEquals("grinder_t1", machine.typeId());
            assertEquals(BlockFace.EAST, machine.direction());
            assertEquals(MachineStatus.ACTIVE, machine.status());
            assertEquals("grind_wheat", machine.selectedRecipeId());
            assertEquals(1.25, machine.wear());

            ResourceNode node = database.loadNodes(islandUuid).stream()
                    .filter(candidate -> candidate.nodeId().equals(nodeId))
                    .findFirst()
                    .orElseThrow();
            assertEquals("iron_ore", node.resourceId());
            assertEquals(500, node.remaining());
            assertEquals(0.75, node.purity());

            assertTrue(database.loadUnlocks(islandUuid).contains("tier_2"));
            assertEquals(12, database.marketDailySold("flour", "2026-06-08"));
            assertEquals(12, database.marketPersonalSold(islandUuid, "flour", "2026-06-08"));
            assertTrue(database.hasContractForTemplate(islandUuid, "bread_supply", "ACTIVE"));
            assertFalse(database.loadContracts(islandUuid, "ACTIVE").isEmpty());
        }
    }

    @Test
    void machineDeleteSurvivesReopen() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000202");
        UUID machineId = UUID.fromString("00000000-0000-0000-0000-000000000203");
        File dataFolder = tempDir.resolve("delete-db").toFile();

        try (DatabaseHandle handle = openDatabase(dataFolder)) {
            MachineInstance machine = new MachineInstance(machineId, islandUuid, ownerUuid, "storage_t1", 1, new BlockKey("world", 1, 2, 3));
            handle.database().saveMachine(machine);
            handle.database().deleteMachine(machineId);
        }

        try (DatabaseHandle handle = openDatabase(dataFolder)) {
            assertTrue(handle.database().loadMachines().isEmpty());
        }
    }

    @Test
    void legacyMachineStatusesLoadAsGoalStatuses() throws Exception {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000241");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000242");

        try (DatabaseHandle handle = openDatabase(tempDir.resolve("legacy-status-db").toFile());
             Connection connection = handle.database().connection();
             Statement statement = connection.createStatement()) {
            insertMachine(statement, islandUuid, ownerUuid,
                    "00000000-0000-0000-0000-000000000243", 0, "RUNNING");
            insertMachine(statement, islandUuid, ownerUuid,
                    "00000000-0000-0000-0000-000000000244", 1, "IDLE");
            insertMachine(statement, islandUuid, ownerUuid,
                    "00000000-0000-0000-0000-000000000245", 2, "INPUT_MISSING");
            insertMachine(statement, islandUuid, ownerUuid,
                    "00000000-0000-0000-0000-000000000246", 3, "LOCKED");

            Set<MachineStatus> statuses = handle.database().loadMachines().stream()
                    .map(MachineInstance::status)
                    .collect(java.util.stream.Collectors.toSet());

            assertTrue(statuses.containsAll(Set.of(
                    MachineStatus.ACTIVE,
                    MachineStatus.SLEEPING,
                    MachineStatus.NO_INPUT,
                    MachineStatus.MAINTENANCE_LOCKED
            )));
        }
    }

    private void insertMachine(Statement statement, UUID islandUuid, UUID ownerUuid,
                               String machineId, int x, String status) throws Exception {
        statement.executeUpdate("""
                INSERT INTO machines(machine_id, island_uuid, owner_uuid, type_id, tier, world, x, y, z, direction,
                  status, last_process_at, wear, config_json, created_at, updated_at)
                VALUES('%s', '%s', '%s', 'grinder_t1', 1, 'world', %d, 64, 0, 'NORTH',
                  '%s', 0, 0, '{}', 1, 1)
                """.formatted(machineId, islandUuid, ownerUuid, x, status));
    }

    @Test
    void itemNetworksAndLinksSurviveReopen() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000302");
        UUID conveyorId = UUID.fromString("00000000-0000-0000-0000-000000000303");
        UUID grinderId = UUID.fromString("00000000-0000-0000-0000-000000000304");
        UUID storageId = UUID.fromString("00000000-0000-0000-0000-000000000305");
        UUID networkId = UUID.fromString("00000000-0000-0000-0000-000000000306");
        UUID bufferInventoryId = UUID.fromString("00000000-0000-0000-0000-000000000307");
        File dataFolder = tempDir.resolve("network-db").toFile();

        try (DatabaseHandle handle = openDatabase(dataFolder)) {
            MachineInstance conveyor = new MachineInstance(conveyorId, islandUuid, ownerUuid, "conveyor_t1", 1,
                    new BlockKey("world", 0, 64, 0));
            conveyor.inputInventoryId(bufferInventoryId);
            MachineInstance grinder = new MachineInstance(grinderId, islandUuid, ownerUuid, "grinder_t1", 1,
                    new BlockKey("world", 1, 64, 0));
            MachineInstance storage = new MachineInstance(storageId, islandUuid, ownerUuid, "storage_t1", 1,
                    new BlockKey("world", -1, 64, 0));
            handle.database().saveMachine(conveyor);
            handle.database().saveMachine(grinder);
            handle.database().saveMachine(storage);
            handle.database().replaceItemNetworks(islandUuid, java.util.List.of(new ItemNetwork(
                    networkId,
                    islandUuid,
                    32,
                    bufferInventoryId,
                    false,
                    1234L,
                    Set.of(conveyorId, grinderId, storageId)
            )));
        }

        try (DatabaseHandle handle = openDatabase(dataFolder)) {
            ItemNetwork network = handle.database().loadItemNetworks(islandUuid).stream().findFirst().orElseThrow();
            assertEquals(networkId, network.networkId());
            assertEquals(32, network.throughputPerMinute());
            assertEquals(bufferInventoryId, network.bufferInventoryId());
            assertEquals(Set.of(conveyorId, grinderId, storageId), network.connectedMachineIds());
            MachineInstance conveyor = handle.database().loadMachines().stream()
                    .filter(machine -> machine.machineId().equals(conveyorId))
                    .findFirst()
                    .orElseThrow();
            assertEquals(networkId, conveyor.itemNetworkId());
        }
    }

    @Test
    void powerNetworksAndLinksSurviveReopen() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000502");
        UUID generatorId = UUID.fromString("00000000-0000-0000-0000-000000000503");
        UUID grinderId = UUID.fromString("00000000-0000-0000-0000-000000000504");
        UUID batteryId = UUID.fromString("00000000-0000-0000-0000-000000000505");
        UUID networkId = UUID.fromString("00000000-0000-0000-0000-000000000506");
        File dataFolder = tempDir.resolve("power-network-db").toFile();

        try (DatabaseHandle handle = openDatabase(dataFolder)) {
            handle.database().saveMachine(new MachineInstance(generatorId, islandUuid, ownerUuid, "bio_generator_t1", 1,
                    new BlockKey("world", 0, 64, 0)));
            handle.database().saveMachine(new MachineInstance(grinderId, islandUuid, ownerUuid, "grinder_t1", 1,
                    new BlockKey("world", 1, 64, 0)));
            handle.database().saveMachine(new MachineInstance(batteryId, islandUuid, ownerUuid, "battery_t1", 1,
                    new BlockKey("world", -1, 64, 0)));
            handle.database().replacePowerNetworks(islandUuid, java.util.List.of(new PowerNetwork(
                    networkId,
                    islandUuid,
                    20.0,
                    8.0,
                    12.0,
                    100.0,
                    1.0,
                    1234L,
                    Set.of(generatorId, grinderId, batteryId)
            )));
        }

        try (DatabaseHandle handle = openDatabase(dataFolder)) {
            PowerNetwork network = handle.database().loadPowerNetworks(islandUuid).stream().findFirst().orElseThrow();
            assertEquals(networkId, network.networkId());
            assertEquals(20.0, network.generationPerSecond());
            assertEquals(8.0, network.consumptionPerSecond());
            assertEquals(12.0, network.batteryStored());
            assertEquals(100.0, network.batteryCapacity());
            assertEquals(1.0, network.powerRatio());
            assertEquals(Set.of(generatorId, grinderId, batteryId), network.connectedMachineIds());
            MachineInstance generator = handle.database().loadMachines().stream()
                    .filter(machine -> machine.machineId().equals(generatorId))
                    .findFirst()
                    .orElseThrow();
            assertEquals(networkId, generator.powerNetworkId());
        }
    }

    private DatabaseHandle openDatabase() {
        return openDatabase(tempDir.resolve(UUID.randomUUID().toString()).toFile());
    }

    private DatabaseHandle openDatabase(File dataFolder) {
        return openDatabase(dataFolder, "data.db");
    }

    private DatabaseHandle openDatabase(File dataFolder, String sqliteFileName) {
        DatabaseService database = new DatabaseService(dataFolder, sqliteFileName);
        database.open();
        return new DatabaseHandle(database);
    }

    private record DatabaseHandle(DatabaseService database) implements AutoCloseable {
        @Override
        public void close() {
            database.close();
        }
    }
}
