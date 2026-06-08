package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MachineService {
    private final DatabaseService database;
    private final MachineDefinitionService definitions;
    private final StorageService storage;
    private final Map<UUID, MachineInstance> machines = new ConcurrentHashMap<>();
    private final Map<BlockKey, UUID> byLocation = new ConcurrentHashMap<>();
    private DirtySaveService dirtySaves;

    public MachineService(DatabaseService database, MachineDefinitionService definitions, StorageService storage) {
        this.database = database;
        this.definitions = definitions;
        this.storage = storage;
    }

    public void load() {
        machines.clear();
        byLocation.clear();
        for (MachineInstance machine : database.loadMachines()) {
            machines.put(machine.machineId(), machine);
            byLocation.put(machine.location(), machine.machineId());
        }
    }

    public Optional<MachineInstance> at(Location location) {
        UUID id = byLocation.get(BlockKey.from(location));
        return id == null ? Optional.empty() : Optional.ofNullable(machines.get(id));
    }

    public MachineInstance create(UUID islandUuid, UUID ownerUuid, String typeId, Location location, BlockFace direction) {
        MachineDefinition definition = definitions.get(typeId).orElseThrow();
        MachineInstance machine = new MachineInstance(UUID.randomUUID(), islandUuid, ownerUuid, typeId, definition.tier(), BlockKey.from(location));
        machine.direction(direction);
        VirtualInventory input = storage.createMachineInventory(islandUuid, machine.machineId(), "MACHINE_INPUT", definition.inputCapacity());
        VirtualInventory output = storage.createMachineInventory(islandUuid, machine.machineId(), "MACHINE_OUTPUT", definition.outputCapacity());
        machine.inputInventoryId(input.inventoryId());
        machine.outputInventoryId(output.inventoryId());
        save(machine);
        return machine;
    }

    public void save(MachineInstance machine) {
        machines.put(machine.machineId(), machine);
        byLocation.put(machine.location(), machine.machineId());
        database.saveMachine(machine);
    }

    public void saveLater(MachineInstance machine) {
        machines.put(machine.machineId(), machine);
        byLocation.put(machine.location(), machine.machineId());
        if (dirtySaves == null) {
            database.saveMachine(machine);
        } else {
            dirtySaves.markMachine(machine);
        }
    }

    public void remove(MachineInstance machine) {
        machine.status(MachineStatus.IDLE);
        machines.remove(machine.machineId());
        byLocation.remove(machine.location());
        database.deleteMachine(machine.machineId());
    }

    public Collection<MachineInstance> all() {
        return new ArrayList<>(machines.values());
    }

    public Collection<MachineInstance> byIsland(UUID islandUuid) {
        return machines.values().stream().filter(machine -> machine.islandUuid().equals(islandUuid)).toList();
    }

    public void dirtySaves(DirtySaveService dirtySaves) {
        this.dirtySaves = dirtySaves;
    }
}
