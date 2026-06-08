package kr.seungmin.satisskyfactory.task;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class DirtySaveService {
    private final JavaPlugin plugin;
    private final DatabaseService database;
    private final Map<UUID, MachineInstance> machines = new ConcurrentHashMap<>();
    private final Map<UUID, VirtualInventory> inventories = new ConcurrentHashMap<>();
    private final Map<UUID, ResourceNode> nodes = new ConcurrentHashMap<>();
    private final Map<UUID, FactoryIsland> islands = new ConcurrentHashMap<>();
    private final Object flushLock = new Object();
    private BukkitTask task;

    public DirtySaveService(JavaPlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void start(long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushSafely, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        flushSafely();
    }

    public void markMachine(MachineInstance machine) {
        machines.put(machine.machineId(), snapshot(machine));
    }

    public void forgetMachine(UUID machineId) {
        machines.remove(machineId);
    }

    public void markInventory(VirtualInventory inventory) {
        inventories.put(inventory.inventoryId(), snapshot(inventory));
    }

    public void markNode(ResourceNode node) {
        nodes.put(node.nodeId(), snapshot(node));
    }

    public void markIsland(FactoryIsland island) {
        islands.put(island.islandUuid(), snapshot(island));
    }

    public void flushSafely() {
        try {
            flush();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Dirty save flush failed: " + exception.getMessage());
        }
    }

    private void flush() {
        synchronized (flushLock) {
            saveBatch("inventory", drain(inventories), inventories, database::saveInventory);
            saveBatch("machine", drain(machines), machines, database::saveMachine);
            saveBatch("node", drain(nodes), nodes, database::saveNode);
            saveBatch("island", drain(islands), islands, database::saveIsland);
        }
    }

    private <T> void saveBatch(String label, Map<UUID, T> snapshot, Map<UUID, T> retryQueue, Consumer<T> saver) {
        snapshot.forEach((id, value) -> {
            try {
                saver.accept(value);
            } catch (RuntimeException exception) {
                retryQueue.put(id, value);
                plugin.getLogger().warning("Dirty save failed for " + label + " " + id + ": " + exception.getMessage());
            }
        });
    }

    private <T> Map<UUID, T> drain(Map<UUID, T> source) {
        Map<UUID, T> snapshot = Map.copyOf(source);
        snapshot.forEach((id, value) -> source.remove(id, value));
        return snapshot;
    }

    private MachineInstance snapshot(MachineInstance machine) {
        MachineInstance copy = new MachineInstance(
                machine.machineId(),
                machine.islandUuid(),
                machine.ownerUuid(),
                machine.typeId(),
                machine.tier(),
                machine.location()
        );
        copy.direction(machine.direction());
        copy.status(machine.status());
        copy.inputInventoryId(machine.inputInventoryId());
        copy.outputInventoryId(machine.outputInventoryId());
        copy.powerNetworkId(machine.powerNetworkId());
        copy.itemNetworkId(machine.itemNetworkId());
        copy.linkedResourceNodeId(machine.linkedResourceNodeId());
        copy.selectedRecipeId(machine.selectedRecipeId());
        copy.lastProcessAt(machine.lastProcessAt());
        copy.wear(machine.wear());
        return copy;
    }

    private VirtualInventory snapshot(VirtualInventory inventory) {
        VirtualInventory copy = new VirtualInventory(
                inventory.inventoryId(),
                inventory.islandUuid(),
                inventory.holderType(),
                inventory.holderId(),
                inventory.capacity()
        );
        inventory.items().forEach(copy::set);
        return copy;
    }

    private ResourceNode snapshot(ResourceNode node) {
        return new ResourceNode(
                node.nodeId(),
                node.islandUuid(),
                node.nodeType(),
                node.resourceId(),
                node.purity(),
                node.remaining(),
                node.maxRemaining(),
                node.regenPerHour(),
                node.requiredMachineTier(),
                node.location(),
                node.updatedAt()
        );
    }

    private FactoryIsland snapshot(FactoryIsland island) {
        FactoryIsland copy = new FactoryIsland(island.islandUuid(), island.ownerUuid());
        copy.tier(island.tier());
        copy.researchPoints(island.researchPoints());
        copy.reputation(island.reputation());
        copy.maintenanceDebt(island.maintenanceDebt());
        copy.maintenanceStatus(island.maintenanceStatus());
        copy.factoryScore(island.factoryScore());
        copy.lastMaintenanceAt(island.lastMaintenanceAt());
        copy.lastTickAt(island.lastTickAt());
        copy.createdAt(island.createdAt());
        copy.emergencyContractsUsedToday(island.emergencyContractsUsedToday());
        return copy;
    }
}
