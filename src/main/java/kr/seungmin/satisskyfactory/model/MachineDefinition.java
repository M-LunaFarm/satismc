package kr.seungmin.satisskyfactory.model;

import org.bukkit.Material;

import java.util.List;

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
        ResourceNodeType nodeType
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
}
