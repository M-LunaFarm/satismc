package kr.seungmin.satisskyfactory.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record MachineDefinition(
        String typeId,
        String displayName,
        Material material,
        int tier,
        int inputCapacity,
        int outputCapacity,
        double powerConsumption,
        double powerGeneration,
        double batteryCapacity,
        int cycleTicks,
        int range,
        int amountPerCycle,
        int logisticsThroughput,
        double wearPerCycle,
        List<String> requiredUnlocks,
        ResourceNodeType nodeType,
        Map<Material, String> harvestDrops
) {
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
