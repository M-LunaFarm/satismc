package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.research.ResearchService;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void unlockConsumesResearchRaisesTierAndStoresGrantedUnlocks() {
        try (DatabaseHandle handle = openDatabase("research-unlock")) {
            ResearchService research = new ResearchService(handle.database(), new TrackingEconomy());
            YamlConfiguration config = load("research.yml");
            config.set("research.unlocks.tier_2_logistics.cost-money", 0);
            research.load(config);
            FactoryIsland island = new FactoryIsland(
                    UUID.fromString("00000000-0000-0000-0000-000000003001"),
                    UUID.fromString("00000000-0000-0000-0000-000000003002")
            );
            island.researchPoints(120);
            handle.database().saveIsland(island);

            ResearchService.UnlockResult result = research.unlock(island, "tier_2_logistics");

            assertEquals(ResearchService.UnlockResult.UNLOCKED, result);
            assertEquals(20, island.researchPoints());
            assertEquals(2, island.tier());
            assertTrue(handle.database().loadUnlocks(island.islandUuid()).contains("tier_2_logistics"));
            assertTrue(handle.database().loadUnlocks(island.islandUuid()).contains("filter_distributor_t1"));
            assertEquals(ResearchService.UnlockResult.ALREADY_UNLOCKED, research.unlock(island, "tier_2_logistics"));
        }
    }

    @Test
    void prerequisitesReputationAndResearchAreRequiredBeforeUnlock() {
        try (DatabaseHandle handle = openDatabase("research-requirements")) {
            ResearchService research = new ResearchService(handle.database(), new TrackingEconomy());
            YamlConfiguration config = load("research.yml");
            config.set("research.unlocks.advanced_processing.cost-money", 0);
            research.load(config);
            FactoryIsland island = new FactoryIsland(
                    UUID.fromString("00000000-0000-0000-0000-000000003101"),
                    UUID.fromString("00000000-0000-0000-0000-000000003102")
            );
            island.researchPoints(200);
            island.reputation(5);
            handle.database().saveIsland(island);

            assertEquals(ResearchService.UnlockResult.MISSING_REQUIREMENT,
                    research.unlock(island, "advanced_processing"));

            handle.database().saveUnlock(island.islandUuid(), "tier_2_logistics");
            island.reputation(0);
            assertEquals(ResearchService.UnlockResult.NOT_ENOUGH_REPUTATION,
                    research.unlock(island, "advanced_processing"));

            island.reputation(5);
            island.researchPoints(100);
            assertEquals(ResearchService.UnlockResult.NOT_ENOUGH_POINTS,
                    research.unlock(island, "advanced_processing"));
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
        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            return true;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return true;
        }

        @Override
        public double balance(OfflinePlayer player) {
            return 0;
        }

        @Override
        public String name() {
            return "Tracking";
        }
    }
}
