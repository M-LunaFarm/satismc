package kr.seungmin.satisskyfactory.logistics;

import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.model.ItemNetwork;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class NetworkRebuildService {
    private static final String NETWORK_UUID_PREFIX = "satisskyfactory:item-network:";

    private final MachineDefinitionService definitions;

    public NetworkRebuildService(MachineDefinitionService definitions) {
        this.definitions = definitions;
    }

    public RebuildResult rebuild(UUID islandUuid, Collection<MachineInstance> islandMachines,
                                 ConnectedMachineFinder connectedMachines, long now) {
        Set<UUID> assigned = new HashSet<>();
        Map<UUID, UUID> assignments = new LinkedHashMap<>();
        List<ItemNetwork> networks = islandMachines.stream()
                .filter(this::isLogisticsRoot)
                .filter(root -> !assigned.contains(root.machineId()))
                .sorted(Comparator.comparing(machine -> machine.location().databaseKey()))
                .map(root -> buildNetwork(islandUuid, root, connectedMachines, assigned, assignments, now))
                .filter(network -> !network.connectedMachineIds().isEmpty())
                .toList();
        return new RebuildResult(networks, assignments);
    }

    private ItemNetwork buildNetwork(UUID islandUuid, MachineInstance root, ConnectedMachineFinder connectedMachines,
                                     Set<UUID> assigned, Map<UUID, UUID> assignments, long now) {
        List<MachineInstance> connected = connectedMachines.connectedTo(root, this::canCarryNetwork).stream()
                .filter(machine -> machine.islandUuid().equals(islandUuid))
                .filter(machine -> !assigned.contains(machine.machineId()))
                .sorted(Comparator.comparing(machine -> machine.location().databaseKey()))
                .toList();
        Set<UUID> machineIds = connected.stream()
                .map(MachineInstance::machineId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        List<ItemNetwork.Route> routes = routes(connected);
        UUID networkId = networkId(islandUuid, connected);
        int throughput = connected.stream()
                .map(machine -> definitions.get(machine.typeId()).orElse(null))
                .filter(definition -> definition != null)
                .mapToInt(MachineDefinition::logisticsThroughput)
                .sum();
        UUID bufferInventoryId = connected.stream()
                .filter(this::isLogisticsRoot)
                .map(MachineInstance::inputInventoryId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
        for (MachineInstance machine : connected) {
            assigned.add(machine.machineId());
            assignments.put(machine.machineId(), networkId);
        }
        return new ItemNetwork(networkId, islandUuid, throughput, bufferInventoryId, false, now, machineIds, routes);
    }

    private List<ItemNetwork.Route> routes(List<MachineInstance> connected) {
        return connected.stream()
                .filter(this::isLogisticsRoot)
                .flatMap(root -> connected.stream()
                        .filter(machine -> !machine.machineId().equals(root.machineId()))
                        .map(machine -> new ItemNetwork.Route(root.machineId(), machine.machineId())))
                .toList();
    }

    private boolean isLogisticsRoot(MachineInstance machine) {
        return definitions.get(machine.typeId())
                .map(MachineDefinition::isLogistics)
                .orElse(false);
    }

    private boolean canCarryNetwork(MachineInstance machine) {
        return definitions.get(machine.typeId())
                .map(definition -> definition.isLogistics() || definition.isStorage())
                .orElse(false);
    }

    private UUID networkId(UUID islandUuid, List<MachineInstance> connected) {
        String firstLocation = connected.stream()
                .filter(this::isLogisticsRoot)
                .map(machine -> machine.location().databaseKey())
                .findFirst()
                .orElseGet(() -> connected.stream()
                        .map(machine -> machine.location().databaseKey())
                        .findFirst()
                        .orElse("empty"));
        return UUID.nameUUIDFromBytes((NETWORK_UUID_PREFIX + islandUuid + ":" + firstLocation).getBytes(StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    public interface ConnectedMachineFinder {
        Collection<MachineInstance> connectedTo(MachineInstance root, Predicate<MachineInstance> traversable);
    }

    public record RebuildResult(List<ItemNetwork> networks, Map<UUID, UUID> assignments) {
        public RebuildResult {
            networks = List.copyOf(networks);
            assignments = Map.copyOf(assignments);
        }
    }
}
