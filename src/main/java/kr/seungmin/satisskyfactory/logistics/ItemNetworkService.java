package kr.seungmin.satisskyfactory.logistics;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.ItemNetwork;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ItemNetworkService {
    private static final String NETWORK_UUID_PREFIX = "satisskyfactory:item-network:";

    private final DatabaseService database;
    private final MachineService machines;
    private final MachineDefinitionService definitions;

    public ItemNetworkService(DatabaseService database, MachineService machines, MachineDefinitionService definitions) {
        this.database = database;
        this.machines = machines;
        this.definitions = definitions;
    }

    public List<ItemNetwork> rebuildIsland(UUID islandUuid) {
        Set<UUID> assigned = new HashSet<>();
        List<ItemNetwork> networks = machines.byIsland(islandUuid).stream()
                .filter(this::isLogisticsRoot)
                .sorted(Comparator.comparing(machine -> machine.location().databaseKey()))
                .map(root -> buildNetwork(islandUuid, root, assigned))
                .filter(network -> !network.connectedMachineIds().isEmpty())
                .toList();
        for (MachineInstance machine : machines.byIsland(islandUuid)) {
            if (!assigned.contains(machine.machineId()) && machine.itemNetworkId() != null) {
                machine.itemNetworkId(null);
                machines.saveLater(machine);
            }
        }
        database.replaceItemNetworks(islandUuid, networks);
        return networks;
    }

    public List<ItemNetwork> load(UUID islandUuid) {
        return database.loadItemNetworks(islandUuid);
    }

    private ItemNetwork buildNetwork(UUID islandUuid, MachineInstance root, Set<UUID> assigned) {
        List<MachineInstance> connected = machines.connectedTo(root, this::canCarryNetwork).stream()
                .filter(machine -> machine.islandUuid().equals(islandUuid))
                .filter(machine -> !assigned.contains(machine.machineId()))
                .sorted(Comparator.comparing(machine -> machine.location().databaseKey()))
                .toList();
        Set<UUID> machineIds = connected.stream()
                .map(MachineInstance::machineId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
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
            if (!networkId.equals(machine.itemNetworkId())) {
                machine.itemNetworkId(networkId);
                machines.saveLater(machine);
            }
        }
        return new ItemNetwork(networkId, islandUuid, throughput, bufferInventoryId, false,
                Instant.now().toEpochMilli(), machineIds);
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
}
