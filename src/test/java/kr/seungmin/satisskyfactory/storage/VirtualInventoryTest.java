package kr.seungmin.satisskyfactory.storage;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualInventoryTest {
    @Test
    void addAndRemovePreserveItemCounts() {
        VirtualInventory inventory = inventory(10);

        assertTrue(inventory.add("wheat", 6));
        assertTrue(inventory.add("iron_ore", 4));
        assertFalse(inventory.add("flour", 1));

        assertEquals(10, inventory.used());
        assertEquals(6, inventory.amount("wheat"));
        assertEquals(4, inventory.amount("iron_ore"));
        assertEquals(0, inventory.amount("flour"));

        assertTrue(inventory.remove("wheat", 2));
        assertFalse(inventory.remove("iron_ore", 5));

        assertEquals(8, inventory.used());
        assertEquals(4, inventory.amount("wheat"));
        assertEquals(4, inventory.amount("iron_ore"));
    }

    @Test
    void removeDeletesZeroCountItems() {
        VirtualInventory inventory = inventory(10);

        assertTrue(inventory.add("bread_box", 3));
        assertTrue(inventory.remove("bread_box", 3));

        assertEquals(0, inventory.used());
        assertEquals(0, inventory.amount("bread_box"));
        assertFalse(inventory.items().containsKey("bread_box"));
    }

    @Test
    void itemSnapshotCannotMutateInventory() {
        VirtualInventory inventory = inventory(10);
        assertTrue(inventory.add("machine_parts", 2));

        Map<String, Long> snapshot = inventory.items();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("machine_parts", 999L));
        assertEquals(2, inventory.amount("machine_parts"));
        assertEquals(2, inventory.used());
    }

    @Test
    void setReplacesAmountWithoutDuplicatingCapacityUse() {
        VirtualInventory inventory = inventory(10);

        inventory.set("fertilizer", 5);
        inventory.set("fertilizer", 3);

        assertEquals(3, inventory.used());
        assertEquals(3, inventory.amount("fertilizer"));
        assertTrue(inventory.canAdd("wheat", 7));
        assertFalse(inventory.canAdd("wheat", 8));
    }

    @Test
    void capacitySupportsLongValuesWithoutOverflow() {
        VirtualInventory inventory = inventory((long) Integer.MAX_VALUE + 100L);

        assertTrue(inventory.add("iron_ore", (long) Integer.MAX_VALUE + 1L));
        assertEquals((long) Integer.MAX_VALUE + 1L, inventory.used());
        assertFalse(inventory.canAdd("iron_ore", Long.MAX_VALUE));
    }

    private VirtualInventory inventory(long capacity) {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return new VirtualInventory(UUID.randomUUID(), islandUuid, "ISLAND", islandUuid.toString(), capacity);
    }
}
