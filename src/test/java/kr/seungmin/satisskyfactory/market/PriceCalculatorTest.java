package kr.seungmin.satisskyfactory.market;

import kr.seungmin.satisskyfactory.item.ItemDefinition;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceCalculatorTest {
    @Test
    void appliesDemandPersonalAndQualityFactors() throws Exception {
        ItemRegistry items = new ItemRegistry();
        register(items, new ItemDefinition("quality_machine_parts", Material.IRON_NUGGET, "Quality", 0, false, 400, true, List.of("quality")));
        PriceCalculator calculator = new PriceCalculator(
                items,
                Map.of("quality_machine_parts", 400L),
                Map.of("quality_machine_parts", 500L),
                Map.of(),
                Map.of("quality", 1.15),
                List.of(new PriceCalculator.PersonalTier(1000, 1.0), new PriceCalculator.PersonalTier(5000, 0.85)),
                true,
                1000,
                0.55,
                1.35,
                0.25
        );

        PriceCalculator.Factors factors = calculator.factors("quality_machine_parts", 10, 10, 4096);

        assertEquals(1.35, factors.serverDemandFactor());
        assertEquals(0.85, factors.personalFactor());
        assertEquals(1.15, factors.qualityFactor());
        assertTrue(calculator.finalPrice("quality_machine_parts", 10, 10, 4096) > calculator.basePrice("quality_machine_parts", 10));
    }

    @Test
    void ignoresQualityTagsWhenQualityIsDisabled() throws Exception {
        ItemRegistry items = new ItemRegistry();
        register(items, new ItemDefinition("tagged_scrap", Material.STICK, "Tagged Scrap", 0, false, 20, false, List.of("quality")));
        PriceCalculator calculator = new PriceCalculator(
                items,
                Map.of("tagged_scrap", 20L),
                Map.of("tagged_scrap", 500L),
                Map.of(),
                Map.of("quality", 1.15),
                List.of(),
                true,
                1000,
                0.55,
                1.35,
                0.25
        );

        assertEquals(1.0, calculator.factors("tagged_scrap", 1, 500, 1).qualityFactor());
    }

    @SuppressWarnings("unchecked")
    private void register(ItemRegistry items, ItemDefinition item) throws Exception {
        Field field = ItemRegistry.class.getDeclaredField("items");
        field.setAccessible(true);
        ((Map<String, ItemDefinition>) field.get(items)).put(item.id(), item);
    }
}
