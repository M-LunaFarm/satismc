package kr.seungmin.satisskyfactory.storage;

import kr.seungmin.satisskyfactory.database.DatabaseService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StorageService {
    private final DatabaseService database;
    private final int defaultCapacity;
    private final Map<UUID, VirtualInventory> cache = new ConcurrentHashMap<>();

    public StorageService(DatabaseService database, int defaultCapacity) {
        this.database = database;
        this.defaultCapacity = defaultCapacity;
    }

    public VirtualInventory islandStorage(UUID islandUuid) {
        Optional<VirtualInventory> existing = database.findInventoryByHolder(islandUuid, "ISLAND", islandUuid.toString());
        if (existing.isPresent()) {
            cache.put(existing.get().inventoryId(), existing.get());
            return existing.get();
        }
        VirtualInventory inventory = new VirtualInventory(UUID.randomUUID(), islandUuid, "ISLAND", islandUuid.toString(), defaultCapacity);
        save(inventory);
        return inventory;
    }

    public VirtualInventory createMachineInventory(UUID islandUuid, UUID machineId, String holderType, int capacity) {
        VirtualInventory inventory = new VirtualInventory(UUID.randomUUID(), islandUuid, holderType, machineId.toString(), capacity);
        save(inventory);
        return inventory;
    }

    public Optional<VirtualInventory> get(UUID inventoryId) {
        if (inventoryId == null) {
            return Optional.empty();
        }
        VirtualInventory cached = cache.get(inventoryId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<VirtualInventory> loaded = database.loadInventory(inventoryId);
        loaded.ifPresent(inventory -> cache.put(inventory.inventoryId(), inventory));
        return loaded;
    }

    public void save(VirtualInventory inventory) {
        cache.put(inventory.inventoryId(), inventory);
        database.saveInventory(inventory);
    }
}
