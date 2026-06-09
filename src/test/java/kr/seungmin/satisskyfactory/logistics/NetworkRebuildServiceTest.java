package kr.seungmin.satisskyfactory.logistics;

import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineIndustry;
import kr.seungmin.satisskyfactory.machine.MachineRole;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.ItemNetwork;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRebuildServiceTest {
    @Test
    void buildsNetworkSnapshotAndAssignments() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000004101");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000004102");
        UUID conveyorId = UUID.fromString("00000000-0000-0000-0000-000000004103");
        UUID grinderId = UUID.fromString("00000000-0000-0000-0000-000000004104");
        UUID storageId = UUID.fromString("00000000-0000-0000-0000-000000004105");
        UUID bufferInventoryId = UUID.fromString("00000000-0000-0000-0000-000000004106");

        MachineDefinitionService definitions = new MachineDefinitionService();
        register(definitions, definition("conveyor_t1", MachineRole.LOGISTICS, 120));
        register(definitions, definition("grinder_t1", MachineRole.PROCESSOR, 0));
        register(definitions, definition("storage_t1", MachineRole.STORAGE, 0));

        MachineInstance conveyor = new MachineInstance(conveyorId, islandUuid, ownerUuid, "conveyor_t1", 1,
                new BlockKey("world", 0, 64, 0));
        conveyor.inputInventoryId(bufferInventoryId);
        MachineInstance grinder = new MachineInstance(grinderId, islandUuid, ownerUuid, "grinder_t1", 1,
                new BlockKey("world", 1, 64, 0));
        MachineInstance storage = new MachineInstance(storageId, islandUuid, ownerUuid, "storage_t1", 1,
                new BlockKey("world", -1, 64, 0));

        NetworkRebuildService.RebuildResult result = new NetworkRebuildService(definitions).rebuild(
                islandUuid,
                List.of(conveyor, grinder, storage),
                (root, traversable) -> {
                    assertFalse(traversable.test(grinder));
                    assertTrue(traversable.test(storage));
                    return List.of(conveyor, grinder, storage);
                },
                1234L
        );

        ItemNetwork network = result.networks().getFirst();
        assertEquals(1, result.networks().size());
        assertEquals(120, network.throughputPerMinute());
        assertEquals(bufferInventoryId, network.bufferInventoryId());
        assertEquals(1234L, network.updatedAt());
        assertEquals(Set.of(conveyorId, grinderId, storageId), network.connectedMachineIds());
        assertEquals(network.networkId(), result.assignments().get(conveyorId));
        assertEquals(network.networkId(), result.assignments().get(grinderId));
        assertEquals(network.networkId(), result.assignments().get(storageId));
    }

    private MachineDefinition definition(String typeId, MachineRole role, int throughput) {
        return new MachineDefinition(
                typeId,
                typeId,
                Material.STONE,
                Material.STONE,
                0,
                1,
                role == MachineRole.STORAGE || role == MachineRole.LOGISTICS ? MachineIndustry.LOGISTICS : MachineIndustry.MANUFACTURING,
                role,
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
