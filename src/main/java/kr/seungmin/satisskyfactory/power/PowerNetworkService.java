package kr.seungmin.satisskyfactory.power;

import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.recipe.RecipeDefinition;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PowerNetworkService {
    private static final String POWER_CHARGE_ITEM = "power_charge";
    private final MachineService machines;
    private final MachineDefinitionService definitions;
    private final RecipeService recipes;
    private final StorageService storage;
    private final Map<UUID, NetworkState> cache = new ConcurrentHashMap<>();
    private long cycleId;

    public PowerNetworkService(MachineService machines, MachineDefinitionService definitions, RecipeService recipes, StorageService storage) {
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
        return cache.computeIfAbsent(islandUuid, this::calculate).ratio();
    }

    public NetworkState state(UUID islandUuid) {
        return cache.computeIfAbsent(islandUuid, this::calculate);
    }

    private NetworkState calculate(UUID islandUuid) {
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
                boolean hasFuel = storage.get(machine.inputInventoryId())
                        .map(inventory -> inventory.amount("biofuel") > 0)
                        .orElse(false)
                        || storage.islandStorage(islandUuid).amount("biofuel") > 0;
                if (hasFuel) {
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
            long excess = stored - maxStored;
            if (islandStorage.remove(POWER_CHARGE_ITEM, excess)) {
                stored = maxStored;
                storage.save(islandStorage);
            }
        }
        double available = generation;
        if (generation >= consumption) {
            long charge = Math.max(0, Math.round(Math.min(generation - consumption, maxStored - stored)));
            if (charge > 0 && islandStorage.add(POWER_CHARGE_ITEM, charge)) {
                stored += charge;
                storage.save(islandStorage);
            }
        } else {
            long discharge = maxStored <= 0 ? 0 : Math.max(0, Math.round(Math.min(stored, consumption - generation)));
            if (discharge > 0 && islandStorage.remove(POWER_CHARGE_ITEM, discharge)) {
                stored -= discharge;
                available += discharge;
                storage.save(islandStorage);
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
                && status != MachineStatus.LOCKED
                && status != MachineStatus.CHUNK_UNLOADED;
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

    public record NetworkState(long cycleId, double ratio, double generation, double consumption, long batteryStored, double batteryCapacity) {
    }
}
