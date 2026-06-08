package kr.seungmin.satisskyfactory.model;

import java.util.Set;
import java.util.UUID;

public record ItemNetwork(
        UUID networkId,
        UUID islandUuid,
        int throughputPerMinute,
        UUID bufferInventoryId,
        boolean dirty,
        long updatedAt,
        Set<UUID> connectedMachineIds
) {
    public ItemNetwork {
        connectedMachineIds = Set.copyOf(connectedMachineIds);
    }
}
