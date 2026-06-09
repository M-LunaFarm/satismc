package kr.seungmin.satisskyfactory.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ItemDefinitionTest {
    @Test
    void storesConfiguredItemFields() {
        ItemDefinition item = new ItemDefinition(
                "iron_plate",
                Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
                "철판",
                0,
                false,
                140,
                List.of("manufacturing")
        );

        assertEquals("iron_plate", item.id());
        assertEquals(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, item.material());
        assertEquals("철판", item.displayName());
        assertEquals(0, item.customModelData());
        assertFalse(item.virtualOnly());
        assertEquals(140, item.basePrice());
        assertEquals(List.of("manufacturing"), item.tags());
    }
}
