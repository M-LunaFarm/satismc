package kr.seungmin.satisskyfactory.maintenance;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineIndustry;
import kr.seungmin.satisskyfactory.machine.MachineRole;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.storage.StorageService;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactoryScoreServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void refreshesFactoryScoreFromInstalledMachines() throws Exception {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000009001");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000009002");
        DatabaseService database = new DatabaseService(tempDir.resolve("score-db").toFile(), "data.db");
        database.open();
        try {
            MachineDefinitionService definitions = new MachineDefinitionService();
            register(definitions, definition("bio_generator_t1", MachineIndustry.POWER, MachineRole.GENERATOR, 12, 5, 12.0, 0.0, 0));
            register(definitions, definition("conveyor_t1", MachineIndustry.LOGISTICS, MachineRole.LOGISTICS, 1, 2, 0.0, 0.0, 120));
            MachineService machines = new MachineService(database, definitions, new StorageService(database, 1000));
            machines.save(new MachineInstance(UUID.randomUUID(), islandUuid, ownerUuid, "bio_generator_t1", 1,
                    new BlockKey("world", 0, 64, 0)));
            machines.save(new MachineInstance(UUID.randomUUID(), islandUuid, ownerUuid, "conveyor_t1", 1,
                    new BlockKey("world", 1, 64, 0)));
            FactoryIsland island = new FactoryIsland(islandUuid, ownerUuid);
            island.tier(2);

            long score = new FactoryScoreService(machines).refreshFactoryScore(island);

            assertEquals(56, score);
            assertEquals(56, island.factoryScore());
        } finally {
            database.close();
        }
    }

    @SuppressWarnings("unchecked")
    private void register(MachineDefinitionService definitions, MachineDefinition definition) throws Exception {
        Field field = MachineDefinitionService.class.getDeclaredField("definitions");
        field.setAccessible(true);
        ((Map<String, MachineDefinition>) field.get(definitions)).put(definition.typeId(), definition);
    }

    private MachineDefinition definition(String typeId, MachineIndustry industry, MachineRole role, long factoryScore,
                                         long maintenanceScore, double generation, double batteryCapacity,
                                         int logisticsThroughput) {
        return new MachineDefinition(
                typeId,
                typeId,
                Material.STONE,
                Material.STONE,
                0,
                1,
                industry,
                role,
                64,
                64,
                0.0,
                generation,
                batteryCapacity,
                40,
                0,
                1,
                logisticsThroughput,
                List.of(),
                List.of(),
                factoryScore,
                maintenanceScore,
                0.0,
                List.of(),
                null,
                0,
                List.of(),
                null,
                Map.of(),
                Map.of(),
                "fertilizer",
                1,
                0.0,
                ""
        );
    }
}
