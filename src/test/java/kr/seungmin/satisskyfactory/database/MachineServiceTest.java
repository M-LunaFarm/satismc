package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void normalRemoveRejectsMachineWithBufferedItems() {
        try (DatabaseHandle handle = openDatabase("normal-remove")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004001");
            bundle.input().add("wheat", 10);
            storage.saveNow(bundle.input());

            assertFalse(machines.remove(bundle.machine()));

            assertTrue(machines.find(bundle.machine().machineId()).isPresent());
            assertEquals(10, storage.get(bundle.input().inventoryId()).orElseThrow().amount("wheat"));
            assertTrue(handle.database().loadMachines().stream()
                    .anyMatch(machine -> machine.machineId().equals(bundle.machine().machineId())));
        }
    }

    @Test
    void forceRemoveFlushesBufferedItemsAndDeletesMachine() {
        try (DatabaseHandle handle = openDatabase("force-remove")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004101");
            bundle.input().add("wheat", 10);
            storage.saveNow(bundle.input());

            machines.forceRemove(bundle.machine());

            assertTrue(machines.find(bundle.machine().machineId()).isEmpty());
            assertTrue(handle.database().loadMachines().stream()
                    .noneMatch(machine -> machine.machineId().equals(bundle.machine().machineId())));
            assertEquals(10, storage.islandStorage(bundle.machine().islandUuid()).amount("wheat"));
            assertTrue(storage.get(bundle.input().inventoryId()).isEmpty());
            assertTrue(storage.get(bundle.output().inventoryId()).isEmpty());
            assertTrue(handle.database().loadInventory(bundle.input().inventoryId()).isEmpty());
            assertTrue(handle.database().loadInventory(bundle.output().inventoryId()).isEmpty());
        }
    }

    @Test
    void reactivateWakesRecoverableMachinesAndBumpsRevision() {
        try (DatabaseHandle handle = openDatabase("reactivate")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004201");
            long before = machines.revision();
            bundle.machine().status(MachineStatus.NO_INPUT);

            machines.reactivate(bundle.machine());

            assertEquals(MachineStatus.SLEEPING, bundle.machine().status());
            assertTrue(machines.revision() > before);
        }
    }

    @Test
    void reactivateDoesNotWakeTerminalMachineStates() {
        try (DatabaseHandle handle = openDatabase("reactivate-terminal")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004301");
            long before = machines.revision();
            bundle.machine().status(MachineStatus.BROKEN);

            machines.reactivate(bundle.machine());

            assertEquals(MachineStatus.BROKEN, bundle.machine().status());
            assertEquals(before, machines.revision());
        }
    }

    @Test
    void reactivatePowerBlockedWakesNoPowerMachinesOnIsland() {
        try (DatabaseHandle handle = openDatabase("reactivate-power")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004401");
            long before = machines.revision();
            bundle.machine().status(MachineStatus.NO_POWER);

            machines.reactivatePowerBlocked(bundle.machine().islandUuid());

            assertEquals(MachineStatus.SLEEPING, bundle.machine().status());
            assertTrue(machines.revision() > before);
        }
    }

    private MachineBundle machineWithInput(StorageService storage, MachineService machines, String machineUuid) {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000004900");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000004901");
        MachineInstance machine = new MachineInstance(UUID.fromString(machineUuid), islandUuid, ownerUuid,
                "grinder_t1", 1, new BlockKey("world", machineUuid.endsWith("4101") ? 1 : 0, 64, 0));
        VirtualInventory input = storage.createMachineInventory(islandUuid, machine.machineId(), "MACHINE_INPUT", 64);
        VirtualInventory output = storage.createMachineInventory(islandUuid, machine.machineId(), "MACHINE_OUTPUT", 64);
        machine.inputInventoryId(input.inventoryId());
        machine.outputInventoryId(output.inventoryId());
        machines.save(machine);
        return new MachineBundle(machine, input, output);
    }

    private DatabaseHandle openDatabase(String name) {
        DatabaseService database = new DatabaseService(tempDir.resolve(name).toFile());
        database.open();
        return new DatabaseHandle(database);
    }

    private record MachineBundle(MachineInstance machine, VirtualInventory input, VirtualInventory output) {
    }

    private record DatabaseHandle(DatabaseService database) implements AutoCloseable {
        @Override
        public void close() {
            database.close();
        }
    }
}
