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
    public String recipeId() {
        return id;
    }

    public Map<String, Long> inputs() {
        return input;
    }

    public Map<String, Long> outputs() {
        return output;
    }

    public long cycleMs() {
        return cycleMillis;
    }

    public double powerCost() {
        return power;
    }

    public boolean supports(String machineType) {
        return machineTypes.contains(machineType);
    }
}
