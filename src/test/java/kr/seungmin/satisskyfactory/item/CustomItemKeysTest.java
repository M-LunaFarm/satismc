package kr.seungmin.satisskyfactory.item;

import org.junit.jupiter.api.Test;

import org.bukkit.inventory.ItemStack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

class CustomItemKeysTest {
    @Test
    void exposesGoalPdcKeyNames() {
        assertEquals("item_id", CustomItemKeys.ITEM_ID);
        assertEquals("machine_type", CustomItemKeys.MACHINE_TYPE);
        assertEquals("machine_tier", CustomItemKeys.MACHINE_TIER);
        assertEquals("internal_uuid", CustomItemKeys.INTERNAL_UUID);
    }

    @Test
    void exposesGoalMachineItemFactoryMethod() throws Exception {
        assertEquals(ItemStack.class, CustomItemFactory.class
                .getMethod("createMachineItem", String.class, int.class)
                .getReturnType());
        assertEquals(Optional.class, CustomItemFactory.class
                .getMethod("machineType", ItemStack.class)
                .getReturnType());
        assertEquals(Optional.class, CustomItemFactory.class
                .getMethod("machineTier", ItemStack.class)
                .getReturnType());
        assertEquals(Optional.class, CustomItemFactory.class
                .getMethod("internalUuid", ItemStack.class)
                .getReturnType());
    }
}
