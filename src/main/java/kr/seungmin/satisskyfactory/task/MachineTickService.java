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
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MachineTickService {
    private final JavaPlugin plugin;
    private final MachineService machines;
    private final MachineDefinitionService definitions;
    private final StorageService storage;
    private final RecipeService recipes;
    private final ResearchService research;
    private final ResourceNodeService nodes;
    private final PowerNetworkService power;
    private final IslandBoostService boosts;
    private final FactoryIslandService islands;
    private final int maxPerCycle;
    private final int maxBackfillCycles;
    private final boolean offlineProductionEnabled;
    private final long offlineMaxMillis;
    private final double offlineEfficiency;
    private final int nodeLinkRadius;
    private final Set<String> recoveryTypes;
    private final double limitedEfficiency;
    private final int limitedMaxOperatingTier;
    private final double lockedRecoveryEfficiency;
    private final int lockedMaxOperatingTier;
    private final double breakWear;
    private final int activeParticleLimit;
    private BukkitTask task;
    private int remainingActiveParticles;
    private long machineSnapshotRevision = Long.MIN_VALUE;
    private List<MachineInstance> machineSnapshot = List.of();
    private final Queue<UUID> activeMachineQueue = new ArrayDeque<>();
    private final Set<UUID> queuedMachines = new HashSet<>();

    public MachineTickService(JavaPlugin plugin, MachineService machines, MachineDefinitionService definitions, StorageService storage,
                              RecipeService recipes, ResearchService research, ResourceNodeService nodes, PowerNetworkService power,
                              IslandBoostService boosts, FactoryIslandService islands, int maxPerCycle,
                              int maxBackfillCycles, boolean offlineProductionEnabled, long offlineMaxMillis,
                              double offlineEfficiency, int nodeLinkRadius, Set<String> recoveryTypes,
                              double limitedEfficiency, int limitedMaxOperatingTier,
                              double lockedRecoveryEfficiency, int lockedMaxOperatingTier, double breakWear,
                              int activeParticleLimit) {
        this.plugin = plugin;
        this.machines = machines;
        this.definitions = definitions;
        this.storage = storage;
        this.recipes = recipes;
        this.research = research;
        this.nodes = nodes;
        this.power = power;
        this.boosts = boosts;
        this.islands = islands;
        this.maxPerCycle = Math.max(1, maxPerCycle);
        this.maxBackfillCycles = Math.max(1, maxBackfillCycles);
        this.offlineProductionEnabled = offlineProductionEnabled;
        this.offlineMaxMillis = Math.max(0L, offlineMaxMillis);
        this.offlineEfficiency = Math.max(0.0, Math.min(1.0, offlineEfficiency));
        this.nodeLinkRadius = Math.max(1, nodeLinkRadius);
        this.recoveryTypes = Set.copyOf(recoveryTypes);
        this.limitedEfficiency = Math.max(0.05, Math.min(1.0, limitedEfficiency));
        this.limitedMaxOperatingTier = Math.max(1, limitedMaxOperatingTier);
        this.lockedRecoveryEfficiency = Math.max(0.05, Math.min(1.0, lockedRecoveryEfficiency));
        this.lockedMaxOperatingTier = Math.max(1, lockedMaxOperatingTier);
        this.breakWear = Math.max(1.0, breakWear);
        this.activeParticleLimit = Math.max(0, activeParticleLimit);
    }

    public void start(long intervalTicks) {
        stop();
        task = SchedulerUtil.repeating(plugin, this::tick, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void tick() {
        power.beginCycle();
        long now = Instant.now().toEpochMilli();
        Set<UUID> touchedIslands = new HashSet<>();
        List<MachineInstance> snapshot = machineSnapshot();
        if (snapshot.isEmpty()) {
            activeMachineQueue.clear();
            queuedMachines.clear();
            return;
        }
        if (activeMachineQueue.isEmpty()) {
            seedActiveQueue(snapshot);
        }
        int limit = Math.min(maxPerCycle, activeMachineQueue.size());
        remainingActiveParticles = activeParticleLimit;
        for (int processed = 0; processed < limit; processed++) {
            UUID machineId = activeMachineQueue.poll();
            if (machineId == null) {
                break;
            }
            queuedMachines.remove(machineId);
            MachineInstance machine = machines.find(machineId).orElse(null);
            if (machine == null) {
                continue;
            }
            definitions.get(machine.typeId()).ifPresent(definition -> {
                ProcessResult result = processMachine(machine, definition);
                if (result.cycles() > 0) {
                    touchedIslands.add(machine.islandUuid());
                }
                if (result.requeue()) {
                    enqueue(machine.machineId());
                }
            });
        }
        refreshTouchedIslands(touchedIslands, now);
    }

    private List<MachineInstance> machineSnapshot() {
        long revision = machines.revision();
        if (revision != machineSnapshotRevision) {
            machineSnapshot = machines.all().stream()
                    .sorted(Comparator.comparing(machine -> machine.machineId().toString()))
                    .toList();
            machineSnapshotRevision = revision;
            activeMachineQueue.clear();
            queuedMachines.clear();
            seedActiveQueue(machineSnapshot);
        }
        return machineSnapshot;
    }

    private void seedActiveQueue(List<MachineInstance> snapshot) {
        snapshot.stream()
                .filter(this::shouldEnterActiveQueue)
                .map(MachineInstance::machineId)
                .forEach(this::enqueue);
    }

    private boolean shouldEnterActiveQueue(MachineInstance machine) {
        return machine.status() == MachineStatus.ACTIVE || machine.status() == MachineStatus.SLEEPING;
    }

    private void enqueue(UUID machineId) {
        if (queuedMachines.add(machineId)) {
            activeMachineQueue.offer(machineId);
        }
    }

    private ProcessResult processMachine(MachineInstance machine, MachineDefinition definition) {
        int cycles = cyclesDue(machine, definition);
        if (cycles <= 0) {
            return new ProcessResult(0, true);
        }
        boolean requeue = true;
        int completedCycles = 0;
        for (int cycle = 0; cycle < cycles; cycle++) {
            if (!process(machine, definition, cycle > 0)) {
                requeue = shouldEnterActiveQueue(machine);
                break;
            }
            completedCycles++;
        }
        return new ProcessResult(completedCycles, requeue && shouldEnterActiveQueue(machine));
    }

    private record ProcessResult(int cycles, boolean requeue) {
    }

    private void refreshTouchedIslands(Set<UUID> touchedIslands, long now) {
        for (UUID islandUuid : touchedIslands) {
            islands.find(islandUuid).ifPresent(island -> {
                if (island.lastTickAt() < now) {
                    island.lastTickAt(now);
                    islands.save(island);
                }
            });
        }
    }

    private int cyclesDue(MachineInstance machine, MachineDefinition definition) {
        long last = machine.lastProcessAt();
        if (last <= 0) {
            return 1;
        }
        long cycleMillis = cycleMillis(machine, definition);
        long elapsed = Instant.now().toEpochMilli() - last;
        if (elapsed < cycleMillis) {
            return 0;
        }
        long effectiveElapsed = effectiveElapsed(elapsed);
        if (effectiveElapsed < cycleMillis) {
            return 1;
        }
        return (int) Math.max(1, Math.min(maxBackfillCycles, effectiveElapsed / cycleMillis));
    }

    private long effectiveElapsed(long elapsed) {
        if (elapsed <= 60_000L) {
            return elapsed;
        }
        if (!offlineProductionEnabled) {
            return 0L;
        }
        long capped = offlineMaxMillis <= 0 ? elapsed : Math.min(elapsed, offlineMaxMillis);
        return Math.round(capped * offlineEfficiency);
    }

    private boolean process(MachineInstance machine, MachineDefinition definition, boolean backfill) {
        if (machine.wear() >= breakWear) {
            if (machine.status() != MachineStatus.BROKEN) {
                setStatus(machine, MachineStatus.BROKEN);
            }
            return false;
        }
        if (!isValidWorld(machine.location())) {
            setStatus(machine, MachineStatus.INVALID_LOCATION);
            return false;
        }
        if (!isChunkLoaded(machine.location())) {
            setStatus(machine, MachineStatus.CHUNK_UNLOADED);
            return false;
        }
        if (!backfill && isCoolingDown(machine, definition)) {
            return false;
        }
        if (!passesMaintenanceGate(machine, definition)) {
            return false;
        }
        double ratio = power.powerRatio(machine.islandUuid());
        if (!definition.isGenerator() && !definition.isBattery() && ratio <= 0.0) {
            setStatus(machine, MachineStatus.NO_POWER);
            return false;
        }
        if (!definition.isGenerator() && !definition.isBattery() && ratio < 1.0
                && ThreadLocalRandom.current().nextDouble() > ratio) {
            setStatus(machine, MachineStatus.SLEEPING);
            return false;
        }
        if (definition.isLogistics()) {
            return processLogistics(machine, definition);
        }
        if (definition.isStorage()) {
            setStatus(machine, MachineStatus.SLEEPING);
            return false;
        }
        if (definition.isGenerator()) {
            return processGenerator(machine, definition);
        }
        if (machine.typeId().equals("harvester_t1")) {
            return processHarvester(machine, definition);
        }
        if (machine.typeId().equals("planter_t1")) {
            return processPlanter(machine, definition);
        }
        if (machine.typeId().equals("fertilizer_sprayer_t1")) {
            return processFertilizerSprayer(machine, definition);
        }
        if (definition.nodeType() != null || machine.typeId().equals("miner_drill_t1")) {
            return processNodeProducer(machine, definition);
        }
        return processRecipe(machine, definition);
    }

    private boolean isCoolingDown(MachineInstance machine, MachineDefinition definition) {
        long last = machine.lastProcessAt();
        if (last <= 0) {
            return false;
        }
        return Instant.now().toEpochMilli() - last < cycleMillis(machine, definition);
    }

    private long cycleMillis(MachineInstance machine, MachineDefinition definition) {
        long definitionCycleMillis = Math.max(1L, definition.cycleTicks()) * 50L;
        if (definition.nodeType() != null || machine.typeId().equals("miner_drill_t1")
                || machine.typeId().equals("harvester_t1") || machine.typeId().equals("planter_t1")
                || machine.typeId().equals("fertilizer_sprayer_t1") || definition.isGenerator()
                || definition.isStorage() || definition.isLogistics()) {
            return definitionCycleMillis;
        }
        return selectedRecipeCycleMillis(machine, definition, definitionCycleMillis);
    }

    private long selectedRecipeCycleMillis(MachineInstance machine, MachineDefinition definition, long fallbackMillis) {
        Optional<FactoryIsland> island = islands.find(machine.islandUuid());
        List<RecipeDefinition> supportedRecipes = recipes.recipesFor(machine.typeId()).stream()
                .filter(recipe -> supportsRecipe(machine, definition, recipe))
                .filter(recipe -> recipeAvailable(island, recipe, definition))
                .filter(recipe -> recipe.cycleMillis() > 0)
                .toList();
        if (supportedRecipes.isEmpty()) {
            return fallbackMillis;
        }
        String selectedRecipeId = machine.selectedRecipeId();
        if (selectedRecipeId != null && !selectedRecipeId.isBlank()) {
            return supportedRecipes.stream()
                    .filter(recipe -> recipe.id().equals(selectedRecipeId))
                    .findFirst()
                    .map(RecipeDefinition::cycleMillis)
                    .orElse(fallbackMillis);
        }
        return supportedRecipes.stream()
                .mapToLong(RecipeDefinition::cycleMillis)
                .min()
                .orElse(fallbackMillis);
    }

    private boolean passesMaintenanceGate(MachineInstance machine, MachineDefinition definition) {
        Optional<FactoryIsland> island = islands.find(machine.islandUuid());
        if (island.isEmpty()) {
            return true;
        }
        MaintenanceStatus status = island.get().maintenanceStatus();
        if (status == MaintenanceStatus.DORMANT) {
            setStatus(machine, MachineStatus.MAINTENANCE_LOCKED);
            return false;
        }
        if (status == MaintenanceStatus.LOCKED) {
            if (!recoveryTypes.contains(machine.typeId()) || definition.tier() > lockedMaxOperatingTier) {
                setStatus(machine, MachineStatus.MAINTENANCE_LOCKED);
                return false;
            }
            if (ThreadLocalRandom.current().nextDouble() > lockedRecoveryEfficiency) {
                setStatus(machine, MachineStatus.SLEEPING);
                return false;
            }
            return true;
        }
        if (status == MaintenanceStatus.LIMITED) {
            if (definition.tier() > limitedMaxOperatingTier) {
                setStatus(machine, MachineStatus.MAINTENANCE_LOCKED);
                return false;
            }
            if (ThreadLocalRandom.current().nextDouble() > limitedEfficiency) {
                setStatus(machine, MachineStatus.SLEEPING);
                return false;
            }
        }
        return true;
    }

    private boolean processGenerator(MachineInstance machine, MachineDefinition definition) {
        if (!consumeGeneratorFuel(machine, generatorFuel(machine, definition))) {
            setStatus(machine, MachineStatus.NO_INPUT);
            return false;
        }
        setStatus(machine, MachineStatus.ACTIVE);
        machines.reactivatePowerBlocked(machine.islandUuid());
        return true;
    }

    private Map<String, Long> generatorFuel(MachineInstance machine, MachineDefinition definition) {
        String selectedRecipeId = machine.selectedRecipeId();
        return recipes.recipesFor(machine.typeId()).stream()
                .filter(recipe -> supportsRecipe(machine, definition, recipe))
                .filter(recipe -> selectedRecipeId == null || selectedRecipeId.isBlank() || recipe.id().equals(selectedRecipeId))
                .map(RecipeDefinition::input)
                .filter(input -> !input.isEmpty())
                .findFirst()
                .orElseGet(() -> Map.of("biofuel", 1L));
    }

    private boolean consumeGeneratorFuel(MachineInstance machine, Map<String, Long> fuel) {
        VirtualInventory input = inputInventory(machine);
        VirtualInventory islandStorage = storage.islandStorage(machine.islandUuid());
        if (fuel.entrySet().stream().anyMatch(entry ->
                input.amount(entry.getKey()) + islandStorage.amount(entry.getKey()) < entry.getValue())) {
            return false;
        }
        boolean changedInput = false;
        boolean changedIsland = false;
        for (Map.Entry<String, Long> entry : fuel.entrySet()) {
            long remaining = entry.getValue();
            long fromInput = Math.min(remaining, input.amount(entry.getKey()));
            if (fromInput > 0 && input.remove(entry.getKey(), fromInput)) {
                remaining -= fromInput;
                changedInput = true;
            }
            if (remaining > 0 && islandStorage.remove(entry.getKey(), remaining)) {
                changedIsland = true;
            }
        }
        if (changedInput) {
            storage.save(input);
        }
        if (changedIsland) {
            storage.save(islandStorage);
        }
        return true;
    }

    private boolean processHarvester(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory output = outputInventory(machine);
        Location location = location(machine.location());
        if (location == null) {
            setStatus(machine, MachineStatus.INVALID_LOCATION);
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
        setStatus(machine, harvested > 0 ? MachineStatus.ACTIVE : MachineStatus.NO_INPUT);
        return harvested > 0;
    }

    private boolean processPlanter(MachineInstance machine, MachineDefinition definition) {
        if (definition.plantRules().isEmpty()) {
            setStatus(machine, MachineStatus.NO_INPUT);
            return false;
        }
        VirtualInventory input = inputInventory(machine);
        Optional<Map.Entry<String, MachineDefinition.PlantRule>> seed = definition.plantRules().entrySet().stream()
                .filter(entry -> input.amount(entry.getKey()) > 0)
                .findFirst();
        if (seed.isEmpty()) {
            setStatus(machine, MachineStatus.NO_INPUT);
            return false;
        }
        Location location = location(machine.location());
        if (location == null) {
            setStatus(machine, MachineStatus.INVALID_LOCATION);
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
                    setStatus(machine, MachineStatus.NO_INPUT);
                    return false;
                }
                crop.setType(rule.crop());
                storage.save(input);
                setStatus(machine, MachineStatus.ACTIVE);
                return true;
            }
        }
        setStatus(machine, MachineStatus.NO_INPUT);
        return false;
    }

    private boolean processFertilizerSprayer(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory input = inputInventory(machine);
        String fertilizerItem = definition.fertilizerItem() == null || definition.fertilizerItem().isBlank()
                ? "fertilizer"
                : definition.fertilizerItem();
        if (input.amount(fertilizerItem) <= 0) {
            setStatus(machine, MachineStatus.NO_INPUT);
            return false;
        }
        Location location = location(machine.location());
        if (location == null) {
            setStatus(machine, MachineStatus.INVALID_LOCATION);
            return false;
        }
        int range = Math.max(1, definition.range());
        int limit = Math.max(1, definition.amountPerCycle());
        int boosted = 0;
        for (int x = -range; x <= range && boosted < limit; x++) {
            for (int z = -range; z <= range && boosted < limit; z++) {
                Block block = location.clone().add(x, 0, z).getBlock();
                if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge()) {
                    continue;
                }
                ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + Math.max(1, definition.growthPerCycle())));
                block.setBlockData(ageable);
                boosted++;
            }
        }
        if (boosted <= 0) {
            setStatus(machine, MachineStatus.NO_INPUT);
            return false;
        }
        if (!input.remove(fertilizerItem, 1)) {
            setStatus(machine, MachineStatus.NO_INPUT);
            return false;
        }
        storage.save(input);
        if (definition.qualityChance() > 0.0 && definition.qualityItem() != null && !definition.qualityItem().isBlank()) {
            grantQualityBonus(machine, definition, boosted);
        }
        setStatus(machine, MachineStatus.ACTIVE);
        return true;
    }

    private void grantQualityBonus(MachineInstance machine, MachineDefinition definition, int boosted) {
        VirtualInventory output = outputInventory(machine);
        long bonus = 0;
        double chance = Math.max(0.0, Math.min(1.0, definition.qualityChance()));
        for (int index = 0; index < boosted; index++) {
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                bonus++;
            }
        }
        if (bonus > 0 && output.add(definition.qualityItem(), bonus)) {
            storage.save(output);
        }
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
                ? nodes.nearest(machine.islandUuid(), machine.location(), nodeLinkRadius, definition.nodeType())
                : nodes.nodes(machine.islandUuid()).stream()
                .filter(candidate -> candidate.nodeId().equals(machine.linkedResourceNodeId()))
                .filter(candidate -> definition.nodeType() == null || definition.nodeType().matches(candidate.nodeType()))
                .findFirst();
        if (node.isEmpty() || node.get().remaining() <= 0 || node.get().requiredMachineTier() > definition.tier()) {
            setStatus(machine, MachineStatus.NO_INPUT);
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
        setStatus(machine, MachineStatus.ACTIVE);
        return true;
    }

    private boolean processRecipe(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory input = inputInventory(machine);
        VirtualInventory output = outputInventory(machine);
        Optional<ResourceNode> recipeNode = recipeNode(machine, definition);
        if (definition.recipeNodeType() != null && recipeNode.isEmpty()) {
            setStatus(machine, MachineStatus.NO_INPUT);
            return false;
        }
        Optional<FactoryIsland> island = islands.find(machine.islandUuid());
        boolean inputReady = false;
        for (RecipeDefinition recipe : recipes.recipesFor(machine.typeId())) {
            if (!supportsRecipe(machine, definition, recipe)) {
                continue;
            }
            if (!recipeAvailable(island, recipe, definition)) {
                continue;
            }
            Map<String, Long> produced = recipeOutput(recipe);
            boolean hasInput = recipe.input().entrySet().stream().allMatch(entry -> input.amount(entry.getKey()) >= entry.getValue());
            if (hasInput) {
                inputReady = true;
            }
            if (hasInput && canAddAll(output, produced)) {
                recipe.input().forEach(input::remove);
                produced.forEach(output::add);
                recipeNode.ifPresent(node -> consumeRecipeNode(machine, definition, node));
                storage.save(input);
                storage.save(output);
                setStatus(machine, MachineStatus.ACTIVE);
                return true;
            }
        }
        setStatus(machine, inputReady ? MachineStatus.OUTPUT_FULL : MachineStatus.NO_INPUT);
        return false;
    }

    private Optional<ResourceNode> recipeNode(MachineInstance machine, MachineDefinition definition) {
        if (definition.recipeNodeType() == null) {
            return Optional.empty();
        }
        Optional<ResourceNode> node = machine.linkedResourceNodeId() == null
                ? nodes.nearest(machine.islandUuid(), machine.location(), nodeLinkRadius, definition.recipeNodeType())
                : nodes.nodes(machine.islandUuid()).stream()
                .filter(candidate -> candidate.nodeId().equals(machine.linkedResourceNodeId()))
                .filter(candidate -> definition.recipeNodeType().matches(candidate.nodeType()))
                .findFirst();
        long required = Math.max(0, definition.recipeNodeUse());
        return node.filter(candidate -> candidate.remaining() >= required && candidate.requiredMachineTier() <= definition.tier());
    }

    private void consumeRecipeNode(MachineInstance machine, MachineDefinition definition, ResourceNode node) {
        machine.linkedResourceNodeId(node.nodeId());
        long required = Math.max(0, definition.recipeNodeUse());
        if (required <= 0) {
            return;
        }
        node.remaining(Math.max(0, node.remaining() - required));
        nodes.save(node);
    }

    private boolean canAddAll(VirtualInventory inventory, Map<String, Long> items) {
        long amount = items.values().stream().mapToLong(Long::longValue).sum();
        return inventory.canAdd("__batch__", amount);
    }

    private Map<String, Long> recipeOutput(RecipeDefinition recipe) {
        Map<String, Long> produced = new HashMap<>(recipe.output());
        recipe.byproducts().forEach((item, amount) -> produced.merge(item, amount, Long::sum));
        if (recipe.qualityChance() > 0.0 && ThreadLocalRandom.current().nextDouble() < Math.min(1.0, recipe.qualityChance())) {
            recipe.output().forEach((item, amount) -> {
                String qualityItem = recipe.qualityItem() == null || recipe.qualityItem().isBlank()
                        ? "quality_" + item
                        : recipe.qualityItem();
                produced.merge(qualityItem, Math.max(1L, amount), Long::sum);
            });
        }
        return produced;
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
            long transfer = moveAny(output, buffer, remaining, definition);
            if (transfer > 0) {
                storage.save(output);
                machines.reactivate(target);
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
                long transfer = fillInput(buffer, input, target, targetDefinition, definition, remaining);
                if (transfer > 0) {
                    storage.save(input);
                    machines.reactivate(target);
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
                    long transfer = fillInput(storageInventory, input, target, targetDefinition, definition, remaining);
                    if (transfer > 0) {
                        storage.save(input);
                        machines.reactivate(target);
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
                long transfer = moveAny(buffer, storageInventory, remaining, definition);
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
        setStatus(machine, moved > 0 ? MachineStatus.ACTIVE : MachineStatus.SLEEPING);
        return moved > 0;
    }

    private boolean canExtendLogisticsNetwork(MachineInstance machine) {
        return definitions.get(machine.typeId())
                .map(definition -> definition.isLogistics() || definition.isStorage())
                .orElse(false);
    }

    private long fillInput(VirtualInventory source, VirtualInventory target, MachineInstance machine,
                           MachineDefinition definition, MachineDefinition logisticsDefinition, long limit) {
        Map<String, Long> desired = desiredInputs(machine, definition);
        long moved = 0;
        for (Map.Entry<String, Long> entry : desired.entrySet()) {
            if (moved >= limit) {
                break;
            }
            if (!passesLogisticsFilter(logisticsDefinition, entry.getKey())) {
                continue;
            }
            long need = Math.max(0, entry.getValue() - target.amount(entry.getKey()));
            long amount = Math.min(need, Math.min(source.amount(entry.getKey()), limit - moved));
            amount = Math.min(amount, target.remainingCapacity());
            if (amount > 0 && source.remove(entry.getKey(), amount) && target.add(entry.getKey(), amount)) {
                moved += amount;
            }
        }
        return moved;
    }

    private Map<String, Long> desiredInputs(MachineInstance machine, MachineDefinition definition) {
        Map<String, Long> desired = new HashMap<>();
        if (definition.isGenerator()) {
            desired.put("biofuel", 16L);
            return desired;
        }
        Optional<FactoryIsland> island = islands.find(machine.islandUuid());
        for (RecipeDefinition recipe : recipes.recipesFor(definition.typeId())) {
            if (!supportsRecipe(machine, definition, recipe)) {
                continue;
            }
            if (!recipeAvailable(island, recipe, definition)) {
                continue;
            }
            recipe.input().forEach((item, amount) -> desired.merge(item, Math.max(amount * 4, amount), Math::max));
        }
        return desired;
    }

    private boolean supportsRecipe(MachineInstance machine, MachineDefinition definition, RecipeDefinition recipe) {
        if (!definition.allowedRecipes().isEmpty() && !definition.allowedRecipes().contains(recipe.id())) {
            return false;
        }
        String selectedRecipeId = machine.selectedRecipeId();
        return selectedRecipeId == null || selectedRecipeId.isBlank() || selectedRecipeId.equals(recipe.id());
    }

    private boolean recipeAvailable(Optional<FactoryIsland> island, RecipeDefinition recipe, MachineDefinition definition) {
        int tier = island.map(FactoryIsland::tier).orElse(definition.tier());
        if (recipe.minTier() > tier) {
            return false;
        }
        return recipe.researchRequired().isEmpty()
                || (island.isPresent() && research.unlocked(island.get()).containsAll(recipe.researchRequired()));
    }

    private long moveAny(VirtualInventory source, VirtualInventory target, long limit, MachineDefinition logisticsDefinition) {
        long moved = 0;
        for (Map.Entry<String, Long> entry : new ArrayList<>(source.items().entrySet())) {
            if (moved >= limit) {
                break;
            }
            if (!passesLogisticsFilter(logisticsDefinition, entry.getKey())) {
                continue;
            }
            long amount = Math.min(entry.getValue(), limit - moved);
            amount = Math.min(amount, target.remainingCapacity());
            if (amount > 0 && source.remove(entry.getKey(), amount) && target.add(entry.getKey(), amount)) {
                moved += amount;
            }
        }
        return moved;
    }

    private boolean passesLogisticsFilter(MachineDefinition definition, String itemId) {
        if (!definition.logisticsAllowedItems().isEmpty() && !definition.logisticsAllowedItems().contains(itemId)) {
            return false;
        }
        return !definition.logisticsBlockedItems().contains(itemId);
    }

    private VirtualInventory inputInventory(MachineInstance machine) {
        return storage.get(machine.inputInventoryId()).orElseGet(() -> storage.islandStorage(machine.islandUuid()));
    }

    private VirtualInventory outputInventory(MachineInstance machine) {
        return storage.get(machine.outputInventoryId()).orElseGet(() -> storage.islandStorage(machine.islandUuid()));
    }

    private void setStatus(MachineInstance machine, MachineStatus status) {
        if (status == MachineStatus.ACTIVE) {
            definitions.get(machine.typeId()).ifPresent(definition ->
                    machine.wear(Math.min(breakWear, machine.wear() + Math.max(0.0, definition.wearPerCycle()))));
            if (machine.wear() >= breakWear) {
                status = MachineStatus.BROKEN;
            }
        }
        machine.status(status);
        machine.lastProcessAt(Instant.now().toEpochMilli());
        machines.saveLater(machine);
        if (status == MachineStatus.ACTIVE) {
            spawnActiveParticle(machine);
        }
    }

    private void spawnActiveParticle(MachineInstance machine) {
        if (remainingActiveParticles <= 0) {
            return;
        }
        Location origin = location(machine.location());
        if (origin == null) {
            return;
        }
        remainingActiveParticles--;
        origin.getWorld().spawnParticle(Particle.CLOUD, origin.clone().add(0.5, 1.05, 0.5),
                1, 0.12, 0.08, 0.12, 0.0);
    }

    private Location location(BlockKey key) {
        World world = Bukkit.getWorld(key.world());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }

    private boolean isChunkLoaded(BlockKey key) {
        World world = Bukkit.getWorld(key.world());
        return world != null && world.isChunkLoaded(key.chunkX(), key.chunkZ());
    }

    private boolean isValidWorld(BlockKey key) {
        return Bukkit.getWorld(key.world()) != null;
    }
}
