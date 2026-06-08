package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyFlowServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void marketSaleRemovesStoragePaysPlayerAndRepaysDebt() {
        try (DatabaseHandle handle = openDatabase("market")) {
            DatabaseService database = handle.database();
            StorageService storage = new StorageService(database, 1000);
            ItemRegistry items = new ItemRegistry();
            items.load(load("items.yml"));
            TrackingEconomy economy = new TrackingEconomy();
            MarketService market = new MarketService(storage, economy, database, items);
            market.load(load("market.yml"));

            UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000001001");
            FactoryIsland island = new FactoryIsland(islandUuid, UUID.fromString("00000000-0000-0000-0000-000000001002"));
            island.maintenanceDebt(100);
            island.maintenanceStatus(MaintenanceStatus.WARNING);
            database.saveIsland(island);

            VirtualInventory inventory = storage.islandStorage(islandUuid);
            assertTrue(inventory.add("flour", 20));
            storage.save(inventory);

            Optional<MarketService.SellResult> result = market.sell(island, null, "flour", 10);

            assertTrue(result.isPresent());
            assertEquals(50, result.get().gross());
            assertEquals(18, result.get().debtRepaid());
            assertEquals(32, result.get().paidToPlayer());
            assertEquals(32.0, economy.deposited());
            assertEquals(82, island.maintenanceDebt());
            assertEquals(10, storage.islandStorage(islandUuid).amount("flour"));
            assertEquals(10, database.marketDailySold("flour", LocalDate.now(ZoneId.systemDefault()).toString()));
            assertEquals(10, database.marketPersonalSold(islandUuid, "flour", LocalDate.now(ZoneId.systemDefault()).toString()));
        }
    }

    @Test
    void lockedMarketSaleUsesHigherDebtRepaymentRate() {
        try (DatabaseHandle handle = openDatabase("locked-market")) {
            DatabaseService database = handle.database();
            StorageService storage = new StorageService(database, 1000);
            ItemRegistry items = new ItemRegistry();
            items.load(load("items.yml"));
            TrackingEconomy economy = new TrackingEconomy();
            MarketService market = new MarketService(storage, economy, database, items);
            market.load(load("market.yml"));

            UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000001101");
            FactoryIsland island = new FactoryIsland(islandUuid, UUID.fromString("00000000-0000-0000-0000-000000001102"));
            island.maintenanceDebt(100);
            island.maintenanceStatus(MaintenanceStatus.LOCKED);
            database.saveIsland(island);
            VirtualInventory inventory = storage.islandStorage(islandUuid);
            assertTrue(inventory.add("flour", 10));
            storage.save(inventory);

            MarketService.SellResult result = market.sell(island, null, "flour", 10).orElseThrow();

            assertEquals(50, result.gross());
            assertEquals(35, result.debtRepaid());
            assertEquals(15, result.paidToPlayer());
            assertEquals(65, island.maintenanceDebt());
            assertEquals(15.0, economy.deposited());
        }
    }

    @Test
    void maintenanceChargeAddsDebtWhenEconomyCannotPay() {
        try (DatabaseHandle handle = openDatabase("maintenance")) {
            DatabaseService database = handle.database();
            MachineDefinitionService definitions = new MachineDefinitionService();
            MachineService machines = new MachineService(database, definitions, new StorageService(database, 1000));
            MaintenanceService maintenance = new MaintenanceService(machines, new TrackingEconomy(), database);
            maintenance.load(load("maintenance.yml"));

            UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000001201");
            FactoryIsland island = new FactoryIsland(islandUuid, UUID.fromString("00000000-0000-0000-0000-000000001202"));

            long due = maintenance.chargeIfDue(island, null, null);

            assertTrue(due > 0);
            assertEquals(due, island.maintenanceDebt());
            assertTrue(island.maintenanceStatus() == MaintenanceStatus.WARNING
                    || island.maintenanceStatus() == MaintenanceStatus.LIMITED
                    || island.maintenanceStatus() == MaintenanceStatus.LOCKED);
            assertEquals(0, maintenance.chargeIfDue(island, null, null));
            assertEquals(due, island.maintenanceDebt());
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
        public double withdrawMaintenance(OfflinePlayer owner, Object island, double amount) {
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
