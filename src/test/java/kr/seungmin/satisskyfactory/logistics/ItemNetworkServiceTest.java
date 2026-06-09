package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.logistics.ItemNetworkService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineIndustry;
import kr.seungmin.satisskyfactory.machine.MachineRole;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.ItemNetwork;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemNetworkServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void rebuildsContiguousLogisticsSnapshot() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000402");
        UUID conveyorId = UUID.fromString("00000000-0000-0000-0000-000000000403");
        UUID grinderId = UUID.fromString("00000000-0000-0000-0000-000000000404");
        UUID storageId = UUID.fromString("00000000-0000-0000-0000-000000000405");
        UUID bufferInventoryId = UUID.fromString("00000000-0000-0000-0000-000000000406");

        DatabaseService database = new DatabaseService(tempDir.resolve("db").toFile());
        database.open();
        try {
            MachineDefinitionService definitions = new MachineDefinitionService();
            register(definitions, definition("conveyor_t1", 32));
            register(definitions, definition("grinder_t1", 0));
            register(definitions, definition("storage_t1", 0));
            MachineService machines = new MachineService(database, definitions, new StorageService(database, 1000));
            MachineInstance conveyor = new MachineInstance(conveyorId, islandUuid, ownerUuid, "conveyor_t1", 1,
                    new BlockKey("world", 0, 64, 0));
            conveyor.inputInventoryId(bufferInventoryId);
            MachineInstance grinder = new MachineInstance(grinderId, islandUuid, ownerUuid, "grinder_t1", 1,
                    new BlockKey("world", 1, 64, 0));
            MachineInstance storage = new MachineInstance(storageId, islandUuid, ownerUuid, "storage_t1", 1,
                    new BlockKey("world", -1, 64, 0));
            machines.save(conveyor);
            machines.save(grinder);
            machines.save(storage);

            ItemNetworkService itemNetworks = new ItemNetworkService(database, machines, definitions);
            ItemNetwork network = itemNetworks.rebuildIsland(islandUuid).stream().findFirst().orElseThrow();

            assertEquals(32, network.throughputPerMinute());
            assertEquals(bufferInventoryId, network.bufferInventoryId());
            assertEquals(Set.of(conveyorId, grinderId, storageId), network.connectedMachineIds());
            assertEquals(Set.of(
                            new ItemNetwork.Route(conveyorId, grinderId),
                            new ItemNetwork.Route(conveyorId, storageId)),
                    Set.copyOf(network.routes()));
            assertEquals(network.networkId(), conveyor.itemNetworkId());
            assertTrue(database.loadItemNetworks(islandUuid).stream()
                    .anyMatch(stored -> stored.connectedMachineIds().equals(network.connectedMachineIds())
                            && Set.copyOf(stored.routes()).equals(Set.copyOf(network.routes()))));
        } finally {
            database.close();
        }
    }

    private MachineDefinition definition(String typeId, int throughput) {
        return new MachineDefinition(
                typeId,
                typeId,
                Material.STONE,
                Material.STONE,
                0,
                1,
                MachineIndustry.UNKNOWN,
                MachineRole.UNKNOWN,
                64,
                64,
                0.0,
                0.0,
                0.0,
                40,
                0,
                1,
                throughput,
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
