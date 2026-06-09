package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractFlowServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void dailyContractConsumesStorageAndAddsRewardsResearchAndReputation() {
        try (DatabaseHandle handle = openDatabase("daily-contract")) {
            DatabaseService database = handle.database();
            StorageService storage = new StorageService(database, 1000);
            TrackingEconomy economy = new TrackingEconomy();
            ContractService contracts = new ContractService(storage, economy, database, new IslandBoostService(null));
            contracts.load(load("contracts.yml"));

            UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000002001");
            FactoryIsland island = new FactoryIsland(islandUuid, UUID.fromString("00000000-0000-0000-0000-000000002002"));
            VirtualInventory inventory = storage.islandStorage(islandUuid);
            assertTrue(inventory.add("bread_box", 32));
            storage.save(inventory);

            ContractService.ActiveContract breadSupply = contracts.activeContracts(island).stream()
                    .filter(contract -> contract.template().id().equals("bread_supply"))
                    .findFirst()
                    .orElseThrow();

            assertTrue(contracts.completeContract(island, null, breadSupply.contractId()).isPresent());

            VirtualInventory updated = storage.islandStorage(islandUuid);
            assertEquals(0, updated.amount("bread_box"));
            assertEquals(35000.0, economy.deposited());
            assertEquals(10, island.researchPoints());
            assertEquals(5, island.reputation());
            assertFalse(database.loadContracts(islandUuid, "COMPLETED").isEmpty());
        }
    }

    @Test
    void emergencyContractRepaysDebtAndTracksDailyUse() {
        try (DatabaseHandle handle = openDatabase("emergency-contract")) {
            DatabaseService database = handle.database();
            StorageService storage = new StorageService(database, 1000);
            TrackingEconomy economy = new TrackingEconomy();
            ContractService contracts = new ContractService(storage, economy, database, new IslandBoostService(null));
            contracts.load(load("contracts.yml"));

            UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000002101");
            FactoryIsland island = new FactoryIsland(islandUuid, UUID.fromString("00000000-0000-0000-0000-000000002102"));
            island.maintenanceDebt(4000);
            database.saveIsland(island);
            VirtualInventory inventory = storage.islandStorage(islandUuid);
            assertTrue(inventory.add("wheat", 64));
            storage.save(inventory);

            assertTrue(contracts.completeEmergency(island, null));

            VirtualInventory updated = storage.islandStorage(islandUuid);
            assertEquals(0, updated.amount("wheat"));
            assertEquals(0, island.maintenanceDebt());
            assertEquals(1, island.emergencyContractsUsedToday());
            assertEquals(1, contracts.emergencyUsedToday(island));
            assertEquals(0.0, economy.deposited());
        }
    }

    @Test
    void emergencyContractCanUseAlternateConfiguredTemplate() {
        try (DatabaseHandle handle = openDatabase("emergency-contract-alternate")) {
            DatabaseService database = handle.database();
            StorageService storage = new StorageService(database, 1000);
            ContractService contracts = new ContractService(storage, new TrackingEconomy(), database, new IslandBoostService(null));
            contracts.load(load("contracts.yml"));

            UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000002501");
            FactoryIsland island = new FactoryIsland(islandUuid, UUID.fromString("00000000-0000-0000-0000-000000002502"));
            island.maintenanceDebt(6000);
            database.saveIsland(island);
            VirtualInventory inventory = storage.islandStorage(islandUuid);
            assertTrue(inventory.add("iron_ore", 64));
            storage.save(inventory);

            assertTrue(contracts.completeEmergency(island, null));

            assertEquals(0, storage.islandStorage(islandUuid).amount("iron_ore"));
            assertEquals(0, island.maintenanceDebt());
            assertTrue(database.loadContracts(islandUuid, "COMPLETED").stream()
                    .anyMatch(contract -> contract.templateId().equals("emergency_manual_mine")));
        }
    }

    @Test
    void activeContractsStayWithinConfiguredSlots() {
        try (DatabaseHandle handle = openDatabase("contract-slots")) {
            ContractService contracts = new ContractService(
                    new StorageService(handle.database(), 1000),
                    new TrackingEconomy(),
                    handle.database(),
                    new IslandBoostService(null)
            );
            contracts.load(load("contracts.yml"));
            FactoryIsland island = new FactoryIsland(
                    UUID.fromString("00000000-0000-0000-0000-000000002201"),
                    UUID.fromString("00000000-0000-0000-0000-000000002202")
            );

            List<ContractService.ActiveContract> active = contracts.activeContracts(island);

            assertEquals(4, active.size());
            assertEquals(3, active.stream().filter(contract -> contract.template().type().equalsIgnoreCase("DAILY")).count());
            assertEquals(1, active.stream().filter(contract -> contract.template().type().equalsIgnoreCase("WEEKLY")).count());
            assertEquals(0, active.stream().filter(contract -> contract.template().type().equalsIgnoreCase("STORY")).count());
            assertEquals(0, active.stream().filter(contract -> contract.template().type().equalsIgnoreCase("MARKET")).count());
        }
    }

    @Test
    void contractSlotBonusAppliesToConfiguredContractTypes() {
        try (DatabaseHandle handle = openDatabase("boosted-contract-slots")) {
            ContractService contracts = new ContractService(
                    new StorageService(handle.database(), 1000),
                    new TrackingEconomy(),
                    handle.database(),
                    new IslandBoostService(null, IslandBoostService.Settings.defaults(), new IslandBoostService.Boosts(1.0, 0, 2))
            );
            YamlConfiguration config = load("contracts.yml");
            config.set("contracts.daily_slots", 1);
            config.set("contracts.weekly_slots", 1);
            config.set("contracts.story_slots", 1);
            config.set("contracts.market_slots", 1);
            config.set("contracts.templates.extra_daily.type", "DAILY");
            config.set("contracts.templates.extra_daily.tier", 1);
            config.set("contracts.templates.extra_daily.required.wheat", 1);
            config.set("contracts.templates.extra_daily.rewards.money", 1);
            config.set("contracts.templates.extra_weekly.type", "WEEKLY");
            config.set("contracts.templates.extra_weekly.tier", 1);
            config.set("contracts.templates.extra_weekly.required.wheat", 1);
            config.set("contracts.templates.extra_weekly.rewards.money", 1);
            contracts.load(config);
            FactoryIsland island = new FactoryIsland(
                    UUID.fromString("00000000-0000-0000-0000-000000002301"),
                    UUID.fromString("00000000-0000-0000-0000-000000002302")
            );

            List<ContractService.ActiveContract> active = contracts.activeContracts(island);

            assertEquals(5, active.size());
            assertEquals(3, active.stream().filter(contract -> contract.template().type().equalsIgnoreCase("DAILY")).count());
            assertEquals(2, active.stream().filter(contract -> contract.template().type().equalsIgnoreCase("WEEKLY")).count());
            assertEquals(0, active.stream().filter(contract -> contract.template().type().equalsIgnoreCase("STORY")).count());
            assertEquals(0, active.stream().filter(contract -> contract.template().type().equalsIgnoreCase("MARKET")).count());
        }
    }

    @Test
    void maxTierContractsDoNotGenerateAboveTheirTierWindow() {
        try (DatabaseHandle handle = openDatabase("contract-max-tier")) {
            ContractService contracts = new ContractService(
                    new StorageService(handle.database(), 1000),
                    new TrackingEconomy(),
                    handle.database(),
                    new IslandBoostService(null)
            );
            YamlConfiguration config = load("contracts.yml");
            config.set("contracts.daily_slots", 2);
            config.set("contracts.weekly_slots", 0);
            config.set("contracts.story_slots", 0);
            config.set("contracts.market_slots", 0);
            config.set("contracts.templates.beginner_wheat.max-tier", 1);
            config.set("contracts.templates.beginner_iron.min-tier", 2);
            config.set("contracts.templates.bread_supply.max-tier", 1);
            contracts.load(config);
            FactoryIsland island = new FactoryIsland(
                    UUID.fromString("00000000-0000-0000-0000-000000002401"),
                    UUID.fromString("00000000-0000-0000-0000-000000002402")
            );
            island.tier(2);

            List<ContractService.ActiveContract> active = contracts.activeContracts(island);

            assertEquals(1, active.size());
            assertEquals("beginner_iron", active.getFirst().template().id());
        }
    }

    private DatabaseHandle openDatabase(String name) {
        DatabaseService database = new DatabaseService(tempDir.resolve(name).toFile());
        database.open();
        return new DatabaseHandle(database);
    }

    private YamlConfiguration load(String name) {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources", name));
    }

    private record DatabaseHandle(DatabaseService database) implements AutoCloseable {
        @Override
        public void close() {
            database.close();
        }
    }

    private static final class TrackingEconomy implements EconomyService {
        private double deposited;

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            deposited += amount;
            return true;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return false;
        }

        @Override
        public double balance(OfflinePlayer player) {
            return 0;
        }

        @Override
        public String name() {
            return "Tracking";
        }

        double deposited() {
            return deposited;
        }
    }
}
