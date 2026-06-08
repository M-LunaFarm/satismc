package kr.seungmin.satisskyfactory.task;

import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.recipe.RecipeDefinition;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class MachineTickService {
    private final JavaPlugin plugin;
    private final MachineService machines;
    private final MachineDefinitionService definitions;
    private final StorageService storage;
    private final RecipeService recipes;
    private final ResourceNodeService nodes;
    private final PowerNetworkService power;
    private final IslandBoostService boosts;
    private final int maxPerCycle;
    private BukkitTask task;

    public MachineTickService(JavaPlugin plugin, MachineService machines, MachineDefinitionService definitions, StorageService storage,
                              RecipeService recipes, ResourceNodeService nodes, PowerNetworkService power,
                              IslandBoostService boosts, int maxPerCycle) {
        this.plugin = plugin;
        this.machines = machines;
        this.definitions = definitions;
        this.storage = storage;
        this.recipes = recipes;
        this.nodes = nodes;
        this.power = power;
        this.boosts = boosts;
        this.maxPerCycle = maxPerCycle;
    }

    public void start(long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void tick() {
        power.beginCycle();
        int processed = 0;
        for (MachineInstance machine : machines.all()) {
            if (processed++ >= maxPerCycle) {
                break;
            }
            definitions.get(machine.typeId()).ifPresent(definition -> process(machine, definition));
        }
    }

    private void process(MachineInstance machine, MachineDefinition definition) {
        double ratio = power.powerRatio(machine.islandUuid());
        if (!definition.isGenerator() && !definition.isBattery() && ratio <= 0.0) {
            setStatus(machine, MachineStatus.NO_POWER);
            return;
        }
        if (!definition.isGenerator() && !definition.isBattery() && ratio < 1.0
                && ThreadLocalRandom.current().nextDouble() > ratio) {
            setStatus(machine, MachineStatus.IDLE);
            return;
        }
        if (definition.isLogistics()) {
            processLogistics(machine, definition);
        } else if (definition.isGenerator()) {
            processGenerator(machine);
        } else if (machine.typeId().equals("harvester_t1")) {
            processHarvester(machine, definition);
        } else if (definition.nodeType() != null || machine.typeId().equals("miner_drill_t1")) {
            processNodeProducer(machine, definition);
        } else {
            processRecipe(machine);
        }
    }

    private void processGenerator(MachineInstance machine) {
        VirtualInventory input = inputInventory(machine);
        if (!input.remove("biofuel", 1)) {
            VirtualInventory islandStorage = storage.islandStorage(machine.islandUuid());
            if (!islandStorage.remove("biofuel", 1)) {
                setStatus(machine, MachineStatus.INPUT_MISSING);
                return;
            }
            storage.save(islandStorage);
        }
        storage.save(input);
        setStatus(machine, MachineStatus.RUNNING);
    }

    private void processHarvester(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory output = outputInventory(machine);
        Location location = location(machine.location());
        if (location == null) {
            return;
        }
        int harvested = 0;
        long amountPerCrop = Math.max(1, Math.round(boosts.boosts(machine.islandUuid()).agricultureBoost()));
        int range = Math.max(1, definition.range());
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                Block block = location.clone().add(x, 0, z).getBlock();
                if (block.getType() == Material.WHEAT && block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
                    if (!output.add("wheat", amountPerCrop)) {
                        setStatus(machine, MachineStatus.OUTPUT_FULL);
                        storage.save(output);
                        return;
                    }
                    ageable.setAge(0);
                    block.setBlockData(ageable);
                    harvested++;
                }
            }
        }
        storage.save(output);
        setStatus(machine, harvested > 0 ? MachineStatus.RUNNING : MachineStatus.INPUT_MISSING);
    }

    private void processNodeProducer(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory output = outputInventory(machine);
        Optional<ResourceNode> node = machine.linkedResourceNodeId() == null
                ? nodes.nearest(machine.islandUuid(), machine.location(), 12, definition.nodeType())
                : nodes.nodes(machine.islandUuid()).stream()
                .filter(candidate -> candidate.nodeId().equals(machine.linkedResourceNodeId()))
                .filter(candidate -> definition.nodeType() == null || candidate.nodeType().equalsIgnoreCase(definition.nodeType().name()))
                .findFirst();
        if (node.isEmpty() || node.get().remaining() <= 0 || node.get().requiredMachineTier() > definition.tier()) {
            setStatus(machine, MachineStatus.INPUT_MISSING);
            return;
        }
        long amount = Math.max(1, Math.round(definition.amountPerCycle() * node.get().purity()));
        amount = Math.min(amount, node.get().remaining());
        if (!output.add(node.get().resourceId(), amount)) {
            setStatus(machine, MachineStatus.OUTPUT_FULL);
            return;
        }
        node.get().remaining(node.get().remaining() - amount);
        machine.linkedResourceNodeId(node.get().nodeId());
        storage.save(output);
        nodes.save(node.get());
        setStatus(machine, MachineStatus.RUNNING);
    }

    private void processRecipe(MachineInstance machine) {
        VirtualInventory input = inputInventory(machine);
        VirtualInventory output = outputInventory(machine);
        for (RecipeDefinition recipe : recipes.recipesFor(machine.typeId())) {
            if (recipe.input().entrySet().stream().allMatch(entry -> input.amount(entry.getKey()) >= entry.getValue())
                    && recipe.output().entrySet().stream().allMatch(entry -> output.canAdd(entry.getKey(), entry.getValue()))) {
                recipe.input().forEach(input::remove);
                recipe.output().forEach(output::add);
                storage.save(input);
                storage.save(output);
                setStatus(machine, MachineStatus.RUNNING);
                return;
            }
        }
        setStatus(machine, MachineStatus.INPUT_MISSING);
    }

    private void processLogistics(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory islandStorage = storage.islandStorage(machine.islandUuid());
        long remaining = definition.logisticsThroughput();
        long moved = 0;
        for (MachineInstance target : machines.byIsland(machine.islandUuid())) {
            if (target.machineId().equals(machine.machineId()) || remaining <= 0) {
                continue;
            }
            VirtualInventory output = outputInventory(target);
            long transfer = moveAny(output, islandStorage, remaining);
            if (transfer > 0) {
                storage.save(output);
                moved += transfer;
                remaining -= transfer;
            }
        }
        if (remaining > 0) {
            for (MachineInstance target : machines.byIsland(machine.islandUuid())) {
                if (target.machineId().equals(machine.machineId()) || remaining <= 0) {
                    continue;
                }
                MachineDefinition targetDefinition = definitions.get(target.typeId()).orElse(null);
                if (targetDefinition == null || targetDefinition.isLogistics()) {
                    continue;
                }
                VirtualInventory input = inputInventory(target);
                long transfer = fillInput(islandStorage, input, targetDefinition, remaining);
                if (transfer > 0) {
                    storage.save(input);
                    moved += transfer;
                    remaining -= transfer;
                }
            }
        }
        if (moved > 0) {
            storage.save(islandStorage);
        }
        setStatus(machine, moved > 0 ? MachineStatus.RUNNING : MachineStatus.IDLE);
    }

    private long fillInput(VirtualInventory source, VirtualInventory target, MachineDefinition definition, long limit) {
        Map<String, Long> desired = desiredInputs(definition);
        long moved = 0;
        for (Map.Entry<String, Long> entry : desired.entrySet()) {
            if (moved >= limit) {
                break;
            }
            long need = Math.max(0, entry.getValue() - target.amount(entry.getKey()));
            long amount = Math.min(need, Math.min(source.amount(entry.getKey()), limit - moved));
            amount = Math.min(amount, Math.max(0, target.capacity() - target.used()));
            if (amount > 0 && source.remove(entry.getKey(), amount) && target.add(entry.getKey(), amount)) {
                moved += amount;
            }
        }
        return moved;
    }

    private Map<String, Long> desiredInputs(MachineDefinition definition) {
        Map<String, Long> desired = new HashMap<>();
        if (definition.isGenerator()) {
            desired.put("biofuel", 16L);
            return desired;
        }
        for (RecipeDefinition recipe : recipes.recipesFor(definition.typeId())) {
            recipe.input().forEach((item, amount) -> desired.merge(item, Math.max(amount * 4, amount), Math::max));
        }
        return desired;
    }

    private long moveAny(VirtualInventory source, VirtualInventory target, long limit) {
        long moved = 0;
        for (Map.Entry<String, Long> entry : new ArrayList<>(source.items().entrySet())) {
            if (moved >= limit) {
                break;
            }
            long amount = Math.min(entry.getValue(), limit - moved);
            amount = Math.min(amount, Math.max(0, target.capacity() - target.used()));
            if (amount > 0 && source.remove(entry.getKey(), amount) && target.add(entry.getKey(), amount)) {
                moved += amount;
            }
        }
        return moved;
    }

    private VirtualInventory inputInventory(MachineInstance machine) {
        return storage.get(machine.inputInventoryId()).orElseGet(() -> storage.islandStorage(machine.islandUuid()));
    }

    private VirtualInventory outputInventory(MachineInstance machine) {
        return storage.get(machine.outputInventoryId()).orElseGet(() -> storage.islandStorage(machine.islandUuid()));
    }

    private void setStatus(MachineInstance machine, MachineStatus status) {
        machine.status(status);
        machine.lastProcessAt(Instant.now().toEpochMilli());
        machines.saveLater(machine);
    }

    private Location location(BlockKey key) {
        World world = Bukkit.getWorld(key.world());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }
}
