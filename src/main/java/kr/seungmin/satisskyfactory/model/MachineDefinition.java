package kr.seungmin.satisskyfactory.model;

import kr.seungmin.satisskyfactory.machine.MachineIndustry;
import kr.seungmin.satisskyfactory.machine.MachineRole;
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
        MachineIndustry industry,
        MachineRole role,
        int inputCapacity,
        int outputCapacity,
        double powerConsumption,
        double powerGeneration,
        double batteryCapacity,
        int cycleTicks,
        int range,
        int amountPerCycle,
        int logisticsThroughput,
        List<String> logisticsAllowedItems,
        List<String> logisticsBlockedItems,
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
        return role == MachineRole.GENERATOR || powerGeneration > 0;
    }

    public boolean isBattery() {
        return role == MachineRole.BATTERY || batteryCapacity > 0;
    }

    public boolean isLogistics() {
        return role == MachineRole.LOGISTICS || logisticsThroughput > 0;
    }

    public boolean isStorage() {
        return role == MachineRole.STORAGE || typeId.startsWith("storage_");
    }
}
