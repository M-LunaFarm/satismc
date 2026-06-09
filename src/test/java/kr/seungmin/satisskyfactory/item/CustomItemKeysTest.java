package kr.seungmin.satisskyfactory.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomItemKeysTest {
    @Test
    void exposesGoalPdcKeyNames() {
        assertEquals("item_id", CustomItemKeys.ITEM_ID);
        assertEquals("machine_type", CustomItemKeys.MACHINE_TYPE);
        assertEquals("machine_tier", CustomItemKeys.MACHINE_TIER);
        assertEquals("internal_uuid", CustomItemKeys.INTERNAL_UUID);
    }
}
