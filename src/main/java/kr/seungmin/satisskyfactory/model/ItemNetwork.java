package kr.seungmin.satisskyfactory.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ItemNetwork(
        UUID networkId,
        UUID islandUuid,
        int throughputPerMinute,
        UUID bufferInventoryId,
        boolean dirty,
        long updatedAt,
        Set<UUID> connectedMachineIds,
        List<Route> routes
) {
    public ItemNetwork {
        connectedMachineIds = Set.copyOf(connectedMachineIds);
        routes = List.copyOf(routes);
    }

    public record Route(UUID fromMachineId, UUID toMachineId) {
    }
}
