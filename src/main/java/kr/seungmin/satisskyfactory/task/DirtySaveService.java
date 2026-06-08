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
        machines.put(machine.machineId(), machine);
    }

    public void markInventory(VirtualInventory inventory) {
        inventories.put(inventory.inventoryId(), inventory);
    }

    public void markNode(ResourceNode node) {
        nodes.put(node.nodeId(), node);
    }

    public void markIsland(FactoryIsland island) {
        islands.put(island.islandUuid(), island);
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
            drain(inventories).values().forEach(database::saveInventory);
            drain(machines).values().forEach(database::saveMachine);
            drain(nodes).values().forEach(database::saveNode);
            drain(islands).values().forEach(database::saveIsland);
        }
    }

    private <T> Map<UUID, T> drain(Map<UUID, T> source) {
        Map<UUID, T> snapshot = Map.copyOf(source);
        snapshot.forEach((id, value) -> source.remove(id, value));
        return snapshot;
    }
}
