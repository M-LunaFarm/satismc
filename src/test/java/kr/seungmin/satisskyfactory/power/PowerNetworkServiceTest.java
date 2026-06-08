package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.PowerNetwork;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerNetworkServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void rebuildsIslandPowerSnapshot() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000602");
        UUID generatorId = UUID.fromString("00000000-0000-0000-0000-000000000603");
        UUID grinderId = UUID.fromString("00000000-0000-0000-0000-000000000604");
        UUID batteryId = UUID.fromString("00000000-0000-0000-0000-000000000605");

        DatabaseService database = new DatabaseService(tempDir.resolve("db").toFile());
        database.open();
        try {
            MachineDefinitionService definitions = new MachineDefinitionService();
            register(definitions, definition("bio_generator_t1", 20.0, 0.0, 0.0));
            register(definitions, definition("grinder_t1", 0.0, 8.0, 0.0));
            register(definitions, definition("battery_t1", 0.0, 0.0, 100.0));
            StorageService storage = new StorageService(database, 1000);
            VirtualInventory islandStorage = storage.islandStorage(islandUuid);
            islandStorage.add("biofuel", 4);
            storage.saveNow(islandStorage);
            MachineService machines = new MachineService(database, definitions, storage);
            MachineInstance generator = new MachineInstance(generatorId, islandUuid, ownerUuid, "bio_generator_t1", 1,
                    new BlockKey("world", 0, 64, 0));
            MachineInstance grinder = new MachineInstance(grinderId, islandUuid, ownerUuid, "grinder_t1", 1,
                    new BlockKey("world", 1, 64, 0));
            MachineInstance battery = new MachineInstance(batteryId, islandUuid, ownerUuid, "battery_t1", 1,
                    new BlockKey("world", -1, 64, 0));
            machines.save(generator);
            machines.save(grinder);
            machines.save(battery);

            PowerNetworkService power = new PowerNetworkService(database, machines, definitions, new RecipeService(), storage);
            PowerNetwork network = power.rebuildIsland(islandUuid).stream().findFirst().orElseThrow();

            assertEquals(20.0, network.generationPerSecond());
            assertEquals(8.0, network.consumptionPerSecond());
            assertEquals(100.0, network.batteryCapacity());
            assertEquals(1.0, network.powerRatio());
            assertEquals(Set.of(generatorId, grinderId, batteryId), network.connectedMachineIds());
            assertEquals(network.networkId(), generator.powerNetworkId());
            assertTrue(database.loadPowerNetworks(islandUuid).stream()
                    .anyMatch(stored -> stored.connectedMachineIds().equals(network.connectedMachineIds())));
        } finally {
            database.close();
        }
    }

    private MachineDefinition definition(String typeId, double generation, double consumption, double batteryCapacity) {
        return new MachineDefinition(
                typeId,
                typeId,
                Material.STONE,
                Material.STONE,
                0,
                1,
                "",
                "",
                64,
                64,
                consumption,
                generation,
                batteryCapacity,
                40,
                0,
                1,
                0,
                List.of(),
                List.of(),
                1,
                1,
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

    @SuppressWarnings("unchecked")
    private void register(MachineDefinitionService definitions, MachineDefinition definition) {
        try {
            Field field = MachineDefinitionService.class.getDeclaredField("definitions");
            field.setAccessible(true);
            ((Map<String, MachineDefinition>) field.get(definitions)).put(definition.typeId(), definition);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
