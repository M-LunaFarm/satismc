package kr.seungmin.satisskyfactory.config;

import kr.seungmin.satisskyfactory.machine.MachineIndustry;
import kr.seungmin.satisskyfactory.machine.MachineRole;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    @Test
    void machineLoaderReadsGoalFields() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("machines.grinder_t1.display-name", "분쇄기");
        config.set("machines.grinder_t1.item-material", "STONECUTTER");
        config.set("machines.grinder_t1.placed-block", "STONECUTTER");
        config.set("machines.grinder_t1.industry", "MANUFACTURING");
        config.set("machines.grinder_t1.role", "PROCESSOR");
        config.set("machines.grinder_t1.cycle-ms", 4000);
        config.set("machines.grinder_t1.power-consumption", 3.0);
        config.set("machines.grinder_t1.power-generation", 0.0);
        config.set("machines.grinder_t1.logistics-throughput-per-minute", 0);
        config.set("machines.grinder_t1.allowed-recipes", java.util.List.of("flour_from_wheat"));

        var machine = new MachineConfigLoader().load(config).get("grinder_t1");

        assertEquals("분쇄기", machine.displayName());
        assertEquals(Material.STONECUTTER, machine.material());
        assertEquals(Material.STONECUTTER, machine.itemMaterial());
        assertEquals(MachineIndustry.MANUFACTURING, machine.industry());
        assertEquals(MachineRole.PROCESSOR, machine.role());
        assertEquals(MachineRole.PROCESSOR, machine.machineRole());
        assertEquals(Material.STONECUTTER, machine.placedBlockMaterial());
        assertEquals(80, machine.cycleTicks());
        assertEquals(4000, machine.cycleMs());
        assertEquals(3.0, machine.powerConsumptionPerSecond());
        assertEquals(0.0, machine.powerGenerationPerSecond());
        assertEquals(0, machine.logisticsThroughputPerMinute());
        assertEquals(java.util.List.of("flour_from_wheat"), machine.allowedRecipes());
    }

    @Test
    void itemLoaderReadsGoalFields() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items.iron_plate.material", "LIGHT_WEIGHTED_PRESSURE_PLATE");
        config.set("items.iron_plate.display-name", "철판");
        config.set("items.iron_plate.base-price", 140);
        config.set("items.iron_plate.quality-enabled", true);
        config.set("items.iron_plate.tags", java.util.List.of("manufacturing"));

        var item = new ItemConfigLoader().load(config).get("iron_plate");

        assertEquals(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, item.material());
        assertEquals("철판", item.displayName());
        assertEquals(140, item.basePrice());
        assertEquals(true, item.qualityEnabled());
        assertEquals(java.util.List.of("manufacturing"), item.tags());
    }

    @Test
    void recipeLoaderReadsGoalFields() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("recipes.flour_from_wheat.machines", java.util.List.of("grinder_t1"));
        config.set("recipes.flour_from_wheat.cycle-ms", 3000);
        config.set("recipes.flour_from_wheat.power-cost", 3.0);
        config.set("recipes.flour_from_wheat.inputs.wheat", 4);
        config.set("recipes.flour_from_wheat.outputs.flour", 1);

        var recipes = new RecipeConfigLoader().load(config);

        assertEquals(1, recipes.size());
        assertTrue(recipes.getFirst().supports("grinder_t1"));
        assertEquals("flour_from_wheat", recipes.getFirst().recipeId());
        assertEquals(3000, recipes.getFirst().cycleMillis());
        assertEquals(3000, recipes.getFirst().cycleMs());
        assertEquals(3.0, recipes.getFirst().power());
        assertEquals(3.0, recipes.getFirst().powerCost());
        assertEquals(4, recipes.getFirst().input().get("wheat"));
        assertEquals(4, recipes.getFirst().inputs().get("wheat"));
        assertEquals(1, recipes.getFirst().output().get("flour"));
        assertEquals(1, recipes.getFirst().outputs().get("flour"));
    }
}
