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
import org.bukkit.Chunk;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public final class MachineService {
    private final DatabaseService database;
    private final MachineDefinitionService definitions;
    private final StorageService storage;
    private final Map<UUID, MachineInstance> machines = new ConcurrentHashMap<>();
    private final Map<BlockKey, UUID> byLocation = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
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
        revision.incrementAndGet();
    }

    public Optional<MachineInstance> at(Location location) {
        UUID id = byLocation.get(BlockKey.from(location));
        return id == null ? Optional.empty() : Optional.ofNullable(machines.get(id));
    }

    public Optional<MachineInstance> find(UUID machineId) {
        return Optional.ofNullable(machines.get(machineId));
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
        revision.incrementAndGet();
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

    public boolean remove(MachineInstance machine) {
        if (hasBufferedItems(machine)) {
            return false;
        }
        delete(machine);
        return true;
    }

    public void forceRemove(MachineInstance machine) {
        if (!flushInventories(machine)) {
            clearInventories(machine);
        }
        delete(machine);
    }

    private void delete(MachineInstance machine) {
        machine.status(MachineStatus.IDLE);
        machines.remove(machine.machineId());
        byLocation.remove(machine.location());
        if (dirtySaves != null) {
            dirtySaves.forgetMachine(machine.machineId());
        }
        database.deleteMachine(machine.machineId());
        revision.incrementAndGet();
    }

    private boolean hasBufferedItems(MachineInstance machine) {
        return machineInventories(machine).stream().anyMatch(inventory -> inventory.used() > 0);
    }

    private boolean flushInventories(MachineInstance machine) {
        VirtualInventory islandStorage = storage.islandStorage(machine.islandUuid());
        List<VirtualInventory> buffers = machineInventories(machine);
        long bufferedItems = buffers.stream().mapToLong(VirtualInventory::used).sum();
        if (!islandStorage.canAdd("__machine_buffer__", bufferedItems)) {
            return false;
        }
        for (VirtualInventory buffer : buffers) {
            for (Map.Entry<String, Long> entry : buffer.items().entrySet()) {
                islandStorage.add(entry.getKey(), entry.getValue());
            }
        }
        for (VirtualInventory buffer : buffers) {
            new ArrayList<>(buffer.items().keySet()).forEach(itemId -> buffer.set(itemId, 0));
            storage.save(buffer);
        }
        storage.save(islandStorage);
        return true;
    }

    private void clearInventories(MachineInstance machine) {
        for (VirtualInventory buffer : machineInventories(machine)) {
            new ArrayList<>(buffer.items().keySet()).forEach(itemId -> buffer.set(itemId, 0));
            storage.save(buffer);
        }
    }

    private List<VirtualInventory> machineInventories(MachineInstance machine) {
        Set<UUID> inventoryIds = new HashSet<>();
        if (machine.inputInventoryId() != null) {
            inventoryIds.add(machine.inputInventoryId());
        }
        if (machine.outputInventoryId() != null) {
            inventoryIds.add(machine.outputInventoryId());
        }
        return inventoryIds.stream()
                .map(storage::get)
                .flatMap(Optional::stream)
                .toList();
    }

    public Collection<MachineInstance> all() {
        return new ArrayList<>(machines.values());
    }

    public long revision() {
        return revision.get();
    }

    public Collection<MachineInstance> byIsland(UUID islandUuid) {
        return machines.values().stream().filter(machine -> machine.islandUuid().equals(islandUuid)).toList();
    }

    public long factoryScore(UUID islandUuid) {
        return factoryScore(islandUuid, 1);
    }

    public long factoryScore(UUID islandUuid, int islandTier) {
        long baseScore = 0;
        long logisticsScore = 0;
        long storageScore = 0;
        long powerScore = 0;
        for (MachineInstance machine : byIsland(islandUuid)) {
            MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
            if (definition == null) {
                continue;
            }
            baseScore += definition.factoryScore();
            if (definition.isLogistics()) {
                logisticsScore += Math.max(1, definition.logisticsThroughput() / 8L);
            }
            if (definition.isStorage()) {
                storageScore += Math.max(1, definition.inputCapacity() / 500L);
            }
            if (definition.isGenerator()) {
                powerScore += Math.max(1, Math.round(definition.powerGeneration() / 4.0));
            }
            if (definition.isBattery()) {
                powerScore += Math.max(1, Math.round(definition.batteryCapacity() / 500.0));
            }
        }
        long islandTierBonus = Math.max(0, islandTier - 1L) * 25L;
        return baseScore + logisticsScore + storageScore + powerScore + islandTierBonus;
    }

    public long maintenanceScore(UUID islandUuid) {
        return byIsland(islandUuid).stream()
                .map(machine -> definitions.get(machine.typeId()).orElse(null))
                .filter(definition -> definition != null)
                .mapToLong(MachineDefinition::maintenanceScore)
                .sum();
    }

    public Collection<MachineInstance> byChunk(Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        return machines.values().stream()
                .filter(machine -> machine.location().world().equals(worldName))
                .filter(machine -> machine.location().chunkX() == chunkX && machine.location().chunkZ() == chunkZ)
                .toList();
    }

    public void markChunkStatus(Chunk chunk, MachineStatus status) {
        for (MachineInstance machine : byChunk(chunk)) {
            if (machine.status() == MachineStatus.BROKEN || machine.status() == status) {
                continue;
            }
            machine.status(status);
            saveLater(machine);
        }
    }

    public Collection<MachineInstance> connectedTo(MachineInstance start) {
        return connectedTo(start, machine -> true);
    }

    public Collection<MachineInstance> connectedTo(MachineInstance start, Predicate<MachineInstance> traversable) {
        Set<UUID> visitedMachines = new HashSet<>();
        Set<BlockKey> visitedLocations = new HashSet<>();
        Queue<BlockKey> queue = new ArrayDeque<>();
        queue.add(start.location());
        visitedLocations.add(start.location());

        while (!queue.isEmpty()) {
            BlockKey location = queue.poll();
            UUID machineId = byLocation.get(location);
            if (machineId == null || !visitedMachines.add(machineId)) {
                continue;
            }
            MachineInstance machine = machines.get(machineId);
            if (machine == null || !machine.islandUuid().equals(start.islandUuid())) {
                continue;
            }
            if (!machine.machineId().equals(start.machineId()) && !traversable.test(machine)) {
                continue;
            }
            for (BlockKey neighbor : neighbors(location)) {
                if (visitedLocations.add(neighbor) && byLocation.containsKey(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return visitedMachines.stream()
                .map(machines::get)
                .filter(machine -> machine != null && machine.islandUuid().equals(start.islandUuid()))
                .toList();
    }

    private List<BlockKey> neighbors(BlockKey location) {
        return List.of(
                location.relative(1, 0, 0),
                location.relative(-1, 0, 0),
                location.relative(0, 1, 0),
                location.relative(0, -1, 0),
                location.relative(0, 0, 1),
                location.relative(0, 0, -1)
        );
    }

    public void dirtySaves(DirtySaveService dirtySaves) {
        this.dirtySaves = dirtySaves;
    }
}
