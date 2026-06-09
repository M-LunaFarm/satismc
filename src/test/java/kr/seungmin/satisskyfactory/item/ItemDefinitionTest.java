package kr.seungmin.satisskyfactory.item;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                true,
                List.of("manufacturing")
        );

        assertEquals("iron_plate", item.id());
        assertEquals("iron_plate", item.itemId());
        assertEquals(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, item.material());
        assertEquals("철판", item.displayName());
        assertEquals(0, item.customModelData());
        assertFalse(item.virtualOnly());
        assertEquals(140, item.basePrice());
        assertEquals(true, item.qualityEnabled());
        assertEquals(List.of("manufacturing"), item.tags());
    }

    @Test
    void registryIdentifiesVirtualOnlyItems() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items.power_charge.material", "REDSTONE");
        config.set("items.power_charge.virtual-only", true);
        config.set("items.flour.material", "SUGAR");

        ItemRegistry registry = new ItemRegistry();
        registry.load(config);

        assertTrue(registry.isVirtualOnly("power_charge"));
        assertFalse(registry.isVirtualOnly("flour"));
        assertFalse(registry.isVirtualOnly("missing_item"));
    }
}
