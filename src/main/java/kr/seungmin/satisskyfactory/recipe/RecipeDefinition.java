package kr.seungmin.satisskyfactory.recipe;

import java.util.Map;
import java.util.List;

public record RecipeDefinition(
        String id,
        List<String> machineTypes,
        Map<String, Long> input,
        Map<String, Long> output,
        Map<String, Long> byproducts,
        long cycleMillis,
        double power,
        int minTier,
        List<String> researchRequired,
        double qualityChance,
        String qualityItem
) {
    public boolean supports(String machineType) {
        return machineTypes.contains(machineType);
    }
}
