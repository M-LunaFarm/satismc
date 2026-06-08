package kr.seungmin.satisskyfactory.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record MachineDefinition(
        String typeId,
        String displayName,
        Material material,
        Material placedMaterial,
        int customModelData,
        int tier,
        String industry,
        String role,
        int inputCapacity,
        int outputCapacity,
        double powerConsumption,
        double powerGeneration,
        double batteryCapacity,
        int cycleTicks,
        int range,
        int amountPerCycle,
        int logisticsThroughput,
        long factoryScore,
        long maintenanceScore,
        double wearPerCycle,
        List<String> allowedRecipes,
        ResourceNodeType recipeNodeType,
        long recipeNodeUse,
        List<String> requiredUnlocks,
        ResourceNodeType nodeType,
        Map<Material, String> harvestDrops,
        Map<String, PlantRule> plantRules,
        String fertilizerItem,
        int growthPerCycle,
        double qualityChance,
        String qualityItem
) {
    public record PlantRule(Material crop, Material soil) {
    }

    public boolean isGenerator() {
        return powerGeneration > 0;
    }

    public boolean isBattery() {
        return batteryCapacity > 0;
    }

    public boolean isLogistics() {
        return logisticsThroughput > 0;
    }

    public boolean isStorage() {
        return typeId.startsWith("storage_");
    }
}
