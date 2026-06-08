package kr.example.satisskyfactory.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VirtualInventory {
    private final UUID inventoryId;
    private final UUID islandUuid;
    private final String holderType;
    private final String holderId;
    private final int capacity;
    private final Map<String, Long> items = new HashMap<>();

    public VirtualInventory(UUID inventoryId, UUID islandUuid, String holderType, String holderId, int capacity) {
        this.inventoryId = inventoryId;
        this.islandUuid = islandUuid;
        this.holderType = holderType;
        this.holderId = holderId;
        this.capacity = capacity;
    }

    public UUID inventoryId() { return inventoryId; }
    public UUID islandUuid() { return islandUuid; }
    public String holderType() { return holderType; }
    public String holderId() { return holderId; }
    public int capacity() { return capacity; }
    public Map<String, Long> items() { return Collections.unmodifiableMap(items); }

    public long used() {
        return items.values().stream().mapToLong(Long::longValue).sum();
    }

    public long amount(String itemId) {
        return items.getOrDefault(itemId, 0L);
    }

    public boolean canAdd(String itemId, long amount) {
        return amount >= 0 && used() + amount <= capacity;
    }

    public boolean add(String itemId, long amount) {
        if (amount <= 0 || !canAdd(itemId, amount)) {
            return false;
        }
        items.merge(itemId, amount, Long::sum);
        return true;
    }

    public boolean remove(String itemId, long amount) {
        if (amount <= 0 || amount(itemId) < amount) {
            return false;
        }
        long next = amount(itemId) - amount;
        if (next == 0) {
            items.remove(itemId);
        } else {
            items.put(itemId, next);
        }
        return true;
    }

    public void set(String itemId, long amount) {
        if (amount <= 0) {
            items.remove(itemId);
        } else {
            items.put(itemId, amount);
        }
    }
}
