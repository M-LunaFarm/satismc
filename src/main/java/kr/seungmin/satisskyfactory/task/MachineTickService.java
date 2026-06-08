package kr.seungmin.satisskyfactory.task;

import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final FactoryIslandService islands;
    private final int maxPerCycle;
    private final int maxBackfillCycles;
    private final Set<String> recoveryTypes;
    private final double limitedEfficiency;
    private final double breakWear;
    private BukkitTask task;
    private int tickCursor;

    public MachineTickService(JavaPlugin plugin, MachineService machines, MachineDefinitionService definitions, StorageService storage,
                              RecipeService recipes, ResourceNodeService nodes, PowerNetworkService power,
                              IslandBoostService boosts, FactoryIslandService islands, int maxPerCycle,
                              int maxBackfillCycles, Set<String> recoveryTypes, double limitedEfficiency, double breakWear) {
        this.plugin = plugin;
        this.machines = machines;
        this.definitions = definitions;
        this.storage = storage;
        this.recipes = recipes;
        this.nodes = nodes;
        this.power = power;
        this.boosts = boosts;
        this.islands = islands;
        this.maxPerCycle = Math.max(1, maxPerCycle);
        this.maxBackfillCycles = Math.max(1, maxBackfillCycles);
        this.recoveryTypes = Set.copyOf(recoveryTypes);
        this.limitedEfficiency = Math.max(0.05, Math.min(1.0, limitedEfficiency));
        this.breakWear = Math.max(1.0, breakWear);
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
        List<MachineInstance> snapshot = machines.all().stream()
                .sorted(Comparator.comparing(machine -> machine.machineId().toString()))
                .toList();
        if (snapshot.isEmpty()) {
            tickCursor = 0;
            return;
        }
        int limit = Math.min(maxPerCycle, snapshot.size());
        int start = Math.floorMod(tickCursor, snapshot.size());
        for (int offset = 0; offset < limit; offset++) {
            MachineInstance machine = snapshot.get((start + offset) % snapshot.size());
            definitions.get(machine.typeId()).ifPresent(definition -> {
                int cycles = cyclesDue(machine, definition);
                for (int cycle = 0; cycle < cycles; cycle++) {
                    if (!process(machine, definition, cycle > 0)) {
                        break;
                    }
                }
            });
        }
        tickCursor = (start + limit) % snapshot.size();
    }

    private int cyclesDue(MachineInstance machine, MachineDefinition definition) {
        long last = machine.lastProcessAt();
        if (last <= 0) {
            return 1;
        }
        long cycleMillis = cycleMillis(definition);
        long elapsed = Instant.now().toEpochMilli() - last;
        if (elapsed < cycleMillis) {
            return 0;
        }
        return (int) Math.max(1, Math.min(maxBackfillCycles, elapsed / cycleMillis));
    }

    private boolean process(MachineInstance machine, MachineDefinition definition, boolean backfill) {
        if (machine.wear() >= breakWear) {
            if (machine.status() != MachineStatus.BROKEN) {
                setStatus(machine, MachineStatus.BROKEN);
            }
            return false;
        }
        if (!backfill && isCoolingDown(machine, definition)) {
            return false;
        }
        if (!passesMaintenanceGate(machine)) {
            return false;
        }
        double ratio = power.powerRatio(machine.islandUuid());
        if (!definition.isGenerator() && !definition.isBattery() && ratio <= 0.0) {
            setStatus(machine, MachineStatus.NO_POWER);
            return false;
        }
        if (!definition.isGenerator() && !definition.isBattery() && ratio < 1.0
                && ThreadLocalRandom.current().nextDouble() > ratio) {
            setStatus(machine, MachineStatus.IDLE);
            return false;
        }
        if (definition.isLogistics()) {
            return processLogistics(machine, definition);
        }
        if (definition.isStorage()) {
            setStatus(machine, MachineStatus.IDLE);
            return false;
        }
        if (definition.isGenerator()) {
            return processGenerator(machine);
        }
        if (machine.typeId().equals("harvester_t1")) {
            return processHarvester(machine, definition);
        }
        if (machine.typeId().equals("planter_t1")) {
            return processPlanter(machine, definition);
        }
        if (definition.nodeType() != null || machine.typeId().equals("miner_drill_t1")) {
            return processNodeProducer(machine, definition);
        }
        return processRecipe(machine);
    }

    private boolean isCoolingDown(MachineInstance machine, MachineDefinition definition) {
        long last = machine.lastProcessAt();
        if (last <= 0) {
            return false;
        }
        return Instant.now().toEpochMilli() - last < cycleMillis(definition);
    }

    private long cycleMillis(MachineDefinition definition) {
        return Math.max(1L, definition.cycleTicks()) * 50L;
    }

    private boolean passesMaintenanceGate(MachineInstance machine) {
        Optional<FactoryIsland> island = islands.find(machine.islandUuid());
        if (island.isEmpty()) {
            return true;
        }
        MaintenanceStatus status = island.get().maintenanceStatus();
        if (status == MaintenanceStatus.LOCKED && !recoveryTypes.contains(machine.typeId())) {
            setStatus(machine, MachineStatus.LOCKED);
            return false;
        }
        if (status == MaintenanceStatus.LIMITED && !recoveryTypes.contains(machine.typeId())
                && ThreadLocalRandom.current().nextDouble() > limitedEfficiency) {
            setStatus(machine, MachineStatus.IDLE);
            return false;
        }
        return true;
    }

    private boolean processGenerator(MachineInstance machine) {
        VirtualInventory input = inputInventory(machine);
        if (!input.remove("biofuel", 1)) {
            VirtualInventory islandStorage = storage.islandStorage(machine.islandUuid());
            if (!islandStorage.remove("biofuel", 1)) {
                setStatus(machine, MachineStatus.INPUT_MISSING);
                return false;
            }
            storage.save(islandStorage);
        }
        storage.save(input);
        setStatus(machine, MachineStatus.RUNNING);
        return true;
    }

    private boolean processHarvester(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory output = outputInventory(machine);
        Location location = location(machine.location());
        if (location == null) {
            return false;
        }
        int harvested = 0;
        Map<Material, String> harvestDrops = definition.harvestDrops().isEmpty()
                ? Map.of(Material.WHEAT, "wheat")
                : definition.harvestDrops();
        long amountPerCrop = Math.max(1, Math.round(boosts.boosts(machine.islandUuid()).agricultureBoost()));
        int range = Math.max(1, definition.range());
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                Block block = location.clone().add(x, 0, z).getBlock();
                String itemId = harvestDrops.get(block.getType());
                if (itemId == null || !isHarvestable(block)) {
                    continue;
                }
                if (!output.add(itemId, amountPerCrop)) {
                    setStatus(machine, MachineStatus.OUTPUT_FULL);
                    storage.save(output);
                    return false;
                }
                resetCrop(block);
                harvested++;
            }
        }
        storage.save(output);
        setStatus(machine, harvested > 0 ? MachineStatus.RUNNING : MachineStatus.INPUT_MISSING);
        return harvested > 0;
    }

    private boolean processPlanter(MachineInstance machine, MachineDefinition definition) {
        if (definition.plantRules().isEmpty()) {
            setStatus(machine, MachineStatus.INPUT_MISSING);
            return false;
        }
        VirtualInventory input = inputInventory(machine);
        Optional<Map.Entry<String, MachineDefinition.PlantRule>> seed = definition.plantRules().entrySet().stream()
                .filter(entry -> input.amount(entry.getKey()) > 0)
                .findFirst();
        if (seed.isEmpty()) {
            setStatus(machine, MachineStatus.INPUT_MISSING);
            return false;
        }
        Location location = location(machine.location());
        if (location == null) {
            return false;
        }
        int range = Math.max(1, definition.range());
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                Block soil = location.clone().add(x, -1, z).getBlock();
                Block crop = soil.getRelative(0, 1, 0);
                MachineDefinition.PlantRule rule = seed.get().getValue();
                if (soil.getType() != rule.soil() || crop.getType() != Material.AIR) {
                    continue;
                }
                if (!input.remove(seed.get().getKey(), 1)) {
                    setStatus(machine, MachineStatus.INPUT_MISSING);
                    return false;
                }
                crop.setType(rule.crop());
                storage.save(input);
                setStatus(machine, MachineStatus.RUNNING);
                return true;
            }
        }
        setStatus(machine, MachineStatus.INPUT_MISSING);
        return false;
    }

    private boolean isHarvestable(Block block) {
        if (block.getType() == Material.SUGAR_CANE) {
            return block.getRelative(0, -1, 0).getType() == Material.SUGAR_CANE;
        }
        return block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    private void resetCrop(Block block) {
        if (block.getType() == Material.SUGAR_CANE) {
            block.setType(Material.AIR);
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable);
        }
    }

    private boolean processNodeProducer(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory output = outputInventory(machine);
        Optional<ResourceNode> node = machine.linkedResourceNodeId() == null
                ? nodes.nearest(machine.islandUuid(), machine.location(), 12, definition.nodeType())
                : nodes.nodes(machine.islandUuid()).stream()
                .filter(candidate -> candidate.nodeId().equals(machine.linkedResourceNodeId()))
                .filter(candidate -> definition.nodeType() == null || candidate.nodeType().equalsIgnoreCase(definition.nodeType().name()))
                .findFirst();
        if (node.isEmpty() || node.get().remaining() <= 0 || node.get().requiredMachineTier() > definition.tier()) {
            setStatus(machine, MachineStatus.INPUT_MISSING);
            return false;
        }
        long amount = Math.max(1, Math.round(definition.amountPerCycle() * node.get().purity()));
        amount = Math.min(amount, node.get().remaining());
        if (!output.add(node.get().resourceId(), amount)) {
            setStatus(machine, MachineStatus.OUTPUT_FULL);
            return false;
        }
        node.get().remaining(node.get().remaining() - amount);
        machine.linkedResourceNodeId(node.get().nodeId());
        storage.save(output);
        nodes.save(node.get());
        setStatus(machine, MachineStatus.RUNNING);
        return true;
    }

    private boolean processRecipe(MachineInstance machine) {
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
                return true;
            }
        }
        setStatus(machine, MachineStatus.INPUT_MISSING);
        return false;
    }

    private boolean processLogistics(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory buffer = inputInventory(machine);
        long remaining = definition.logisticsThroughput();
        long moved = 0;
        List<MachineInstance> network = machines.connectedTo(machine, this::canExtendLogisticsNetwork).stream()
                .sorted(Comparator.comparing(candidate -> candidate.machineId().toString()))
                .toList();
        List<MachineInstance> storageNodes = network.stream()
                .filter(candidate -> !candidate.machineId().equals(machine.machineId()))
                .filter(candidate -> definitions.get(candidate.typeId()).map(MachineDefinition::isStorage).orElse(false))
                .toList();
        for (MachineInstance target : network) {
            if (target.machineId().equals(machine.machineId()) || remaining <= 0) {
                continue;
            }
            MachineDefinition targetDefinition = definitions.get(target.typeId()).orElse(null);
            if (targetDefinition == null || targetDefinition.isLogistics() || targetDefinition.isStorage()) {
                continue;
            }
            VirtualInventory output = outputInventory(target);
            long transfer = moveAny(output, buffer, remaining);
            if (transfer > 0) {
                storage.save(output);
                moved += transfer;
                remaining -= transfer;
            }
        }
        if (remaining > 0) {
            for (MachineInstance target : network) {
                if (target.machineId().equals(machine.machineId()) || remaining <= 0) {
                    continue;
                }
                MachineDefinition targetDefinition = definitions.get(target.typeId()).orElse(null);
                if (targetDefinition == null || targetDefinition.isLogistics() || targetDefinition.isStorage()) {
                    continue;
                }
                VirtualInventory input = inputInventory(target);
                long transfer = fillInput(buffer, input, targetDefinition, remaining);
                if (transfer > 0) {
                    storage.save(input);
                    moved += transfer;
                    remaining -= transfer;
                }
            }
        }
        if (remaining > 0) {
            for (MachineInstance storageNode : storageNodes) {
                if (remaining <= 0) {
                    break;
                }
                VirtualInventory storageInventory = inputInventory(storageNode);
                long movedFromStorage = 0;
                for (MachineInstance target : network) {
                    if (target.machineId().equals(machine.machineId()) || target.machineId().equals(storageNode.machineId()) || remaining <= 0) {
                        continue;
                    }
                    MachineDefinition targetDefinition = definitions.get(target.typeId()).orElse(null);
                    if (targetDefinition == null || targetDefinition.isLogistics() || targetDefinition.isStorage()) {
                        continue;
                    }
                    VirtualInventory input = inputInventory(target);
                    long transfer = fillInput(storageInventory, input, targetDefinition, remaining);
                    if (transfer > 0) {
                        storage.save(input);
                        moved += transfer;
                        movedFromStorage += transfer;
                        remaining -= transfer;
                    }
                }
                if (movedFromStorage > 0) {
                    storage.save(storageInventory);
                }
            }
        }
        if (remaining > 0) {
            for (MachineInstance storageNode : storageNodes) {
                if (remaining <= 0) {
                    break;
                }
                VirtualInventory storageInventory = inputInventory(storageNode);
                long transfer = moveAny(buffer, storageInventory, remaining);
                if (transfer > 0) {
                    storage.save(storageInventory);
                    moved += transfer;
                    remaining -= transfer;
                }
            }
        }
        if (moved > 0) {
            storage.save(buffer);
        }
        setStatus(machine, moved > 0 ? MachineStatus.RUNNING : MachineStatus.IDLE);
        return moved > 0;
    }

    private boolean canExtendLogisticsNetwork(MachineInstance machine) {
        return definitions.get(machine.typeId())
                .map(definition -> definition.isLogistics() || definition.isStorage())
                .orElse(false);
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
        if (status == MachineStatus.RUNNING) {
            definitions.get(machine.typeId()).ifPresent(definition ->
                    machine.wear(Math.min(breakWear, machine.wear() + Math.max(0.0, definition.wearPerCycle()))));
            if (machine.wear() >= breakWear) {
                status = MachineStatus.BROKEN;
            }
        }
        machine.status(status);
        machine.lastProcessAt(Instant.now().toEpochMilli());
        machines.saveLater(machine);
    }

    private Location location(BlockKey key) {
        World world = Bukkit.getWorld(key.world());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }
}
