package kr.seungmin.satisskyfactory.power;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.model.PowerNetwork;
import kr.seungmin.satisskyfactory.recipe.RecipeDefinition;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PowerNetworkService {
    private static final String NETWORK_UUID_PREFIX = "satisskyfactory:power-network:";
    private static final String POWER_CHARGE_ITEM = "power_charge";
    private final DatabaseService database;
    private final MachineService machines;
    private final MachineDefinitionService definitions;
    private final RecipeService recipes;
    private final StorageService storage;
    private final Map<UUID, NetworkState> cache = new ConcurrentHashMap<>();
    private long cycleId;

    public PowerNetworkService(DatabaseService database, MachineService machines, MachineDefinitionService definitions,
                               RecipeService recipes, StorageService storage) {
        this.database = database;
        this.machines = machines;
        this.definitions = definitions;
        this.recipes = recipes;
        this.storage = storage;
    }

    public void beginCycle() {
        cycleId++;
        cache.clear();
    }

    public double powerRatio(UUID islandUuid) {
        return cache.computeIfAbsent(islandUuid, uuid -> calculate(uuid, true)).ratio();
    }

    public NetworkState state(UUID islandUuid) {
        NetworkState cached = cache.get(islandUuid);
        return cached == null ? calculate(islandUuid, false) : cached;
    }

    public List<PowerNetwork> rebuildIsland(UUID islandUuid) {
        cache.remove(islandUuid);
        List<MachineInstance> connected = machines.byIsland(islandUuid).stream()
                .filter(this::hasPowerRole)
                .sorted(Comparator.comparing(machine -> machine.location().databaseKey()))
                .toList();
        if (connected.isEmpty()) {
            clearIslandPowerIds(islandUuid);
            database.replacePowerNetworks(islandUuid, List.of());
            return List.of();
        }
        UUID networkId = networkId(islandUuid);
        for (MachineInstance machine : connected) {
            if (!networkId.equals(machine.powerNetworkId())) {
                machine.powerNetworkId(networkId);
                machines.saveLater(machine);
            }
        }
        Set<UUID> connectedMachineIds = connected.stream()
                .map(MachineInstance::machineId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        NetworkState state = state(islandUuid);
        PowerNetwork network = new PowerNetwork(
                networkId,
                islandUuid,
                state.generation(),
                state.consumption(),
                state.batteryStored(),
                state.batteryCapacity(),
                state.ratio(),
                Instant.now().toEpochMilli(),
                connectedMachineIds
        );
        database.replacePowerNetworks(islandUuid, List.of(network));
        machines.reactivatePowerBlocked(islandUuid);
        return List.of(network);
    }

    public List<PowerNetwork> load(UUID islandUuid) {
        return database.loadPowerNetworks(islandUuid);
    }

    private void clearIslandPowerIds(UUID islandUuid) {
        for (MachineInstance machine : machines.byIsland(islandUuid)) {
            if (machine.powerNetworkId() != null) {
                machine.powerNetworkId(null);
                machines.saveLater(machine);
            }
        }
    }

    private boolean hasPowerRole(MachineInstance machine) {
        return definitions.get(machine.typeId())
                .map(definition -> definition.isGenerator()
                        || definition.isBattery()
                        || definition.powerConsumption() > 0.0)
                .orElse(false);
    }

    private UUID networkId(UUID islandUuid) {
        return UUID.nameUUIDFromBytes((NETWORK_UUID_PREFIX + islandUuid).getBytes(StandardCharsets.UTF_8));
    }

    private NetworkState calculate(UUID islandUuid, boolean mutateBattery) {
        double generation = 0;
        double consumption = 0;
        double batteryCapacity = 0;
        for (MachineInstance machine : machines.byIsland(islandUuid)) {
            MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
            if (definition == null) {
                continue;
            }
            if (!canParticipate(machine)) {
                continue;
            }
            if (definition.isGenerator()) {
                if (hasGeneratorFuel(machine, definition)) {
                    generation += definition.powerGeneration();
                }
            } else if (definition.isBattery()) {
                batteryCapacity += definition.batteryCapacity();
            } else {
                consumption += consumptionFor(machine, definition);
            }
        }
        VirtualInventory islandStorage = storage.islandStorage(islandUuid);
        long stored = islandStorage.amount(POWER_CHARGE_ITEM);
        long maxStored = Math.max(0, Math.round(batteryCapacity));
        if (stored > maxStored) {
            if (mutateBattery) {
                long excess = stored - maxStored;
                if (islandStorage.remove(POWER_CHARGE_ITEM, excess)) {
                    stored = maxStored;
                    storage.save(islandStorage);
                }
            } else {
                stored = maxStored;
            }
        }
        double available = generation;
        if (generation >= consumption) {
            long charge = Math.max(0, Math.round(Math.min(generation - consumption, maxStored - stored)));
            if (charge > 0) {
                if (mutateBattery && islandStorage.add(POWER_CHARGE_ITEM, charge)) {
                    stored += charge;
                    storage.save(islandStorage);
                } else if (!mutateBattery) {
                    stored += charge;
                }
            }
        } else {
            long discharge = maxStored <= 0 ? 0 : Math.max(0, Math.round(Math.min(stored, consumption - generation)));
            if (discharge > 0) {
                if (mutateBattery && islandStorage.remove(POWER_CHARGE_ITEM, discharge)) {
                    stored -= discharge;
                    available += discharge;
                    storage.save(islandStorage);
                } else if (!mutateBattery) {
                    stored -= discharge;
                    available += discharge;
                }
            }
        }
        if (consumption <= 0) {
            return new NetworkState(cycleId, 1.0, generation, consumption, stored, batteryCapacity);
        }
        double ratio = Math.max(0.0, Math.min(1.0, available / consumption));
        return new NetworkState(cycleId, ratio, generation, consumption, stored, batteryCapacity);
    }

    private boolean canParticipate(MachineInstance machine) {
        MachineStatus status = machine.status();
        return status != MachineStatus.BROKEN
                && status != MachineStatus.MAINTENANCE_LOCKED
                && status != MachineStatus.CHUNK_UNLOADED
                && status != MachineStatus.INVALID_LOCATION;
    }

    private double consumptionFor(MachineInstance machine, MachineDefinition definition) {
        String selectedRecipeId = machine.selectedRecipeId();
        if (selectedRecipeId == null || selectedRecipeId.isBlank()) {
            return definition.powerConsumption();
        }
        return recipes.recipesFor(machine.typeId()).stream()
                .filter(recipe -> recipe.id().equals(selectedRecipeId))
                .filter(recipe -> supportsRecipe(definition, recipe))
                .mapToDouble(RecipeDefinition::power)
                .filter(power -> power > 0)
                .findFirst()
                .orElse(definition.powerConsumption());
    }

    private boolean supportsRecipe(MachineDefinition definition, RecipeDefinition recipe) {
        return definition.allowedRecipes().isEmpty() || definition.allowedRecipes().contains(recipe.id());
    }

    private boolean hasGeneratorFuel(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory input = storage.get(machine.inputInventoryId()).orElse(null);
        VirtualInventory islandStorage = storage.islandStorage(machine.islandUuid());
        return generatorFuel(machine, definition).entrySet().stream()
                .allMatch(entry -> amount(input, entry.getKey()) + islandStorage.amount(entry.getKey()) >= entry.getValue());
    }

    private Map<String, Long> generatorFuel(MachineInstance machine, MachineDefinition definition) {
        String selectedRecipeId = machine.selectedRecipeId();
        return recipes.recipesFor(machine.typeId()).stream()
                .filter(recipe -> supportsRecipe(definition, recipe))
                .filter(recipe -> selectedRecipeId == null || selectedRecipeId.isBlank() || recipe.id().equals(selectedRecipeId))
                .map(RecipeDefinition::input)
                .filter(input -> !input.isEmpty())
                .findFirst()
                .orElseGet(() -> Map.of("biofuel", 1L));
    }

    private long amount(VirtualInventory inventory, String itemId) {
        return inventory == null ? 0 : inventory.amount(itemId);
    }

    public record NetworkState(long cycleId, double ratio, double generation, double consumption, long batteryStored, double batteryCapacity) {
    }
}
