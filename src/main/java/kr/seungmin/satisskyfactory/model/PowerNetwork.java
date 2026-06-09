package kr.seungmin.satisskyfactory.model;

import java.util.Set;
import java.util.UUID;

public record PowerNetwork(
        UUID networkId,
        UUID islandUuid,
        double generationPerSecond,
        double consumptionPerSecond,
        double batteryStored,
        double batteryCapacity,
        double powerRatio,
        long updatedAt,
        Set<UUID> connectedMachineIds
) {
    public PowerNetwork {
        connectedMachineIds = Set.copyOf(connectedMachineIds);
    }

    public Set<UUID> nodeMachineIds() {
        return connectedMachineIds;
    }
}
