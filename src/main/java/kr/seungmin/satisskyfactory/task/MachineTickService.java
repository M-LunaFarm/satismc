package kr.seungmin.satisskyfactory.task;

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
import java.util.Optional;

public final class MachineTickService {
    private final JavaPlugin plugin;
    private final MachineService machines;
    private final MachineDefinitionService definitions;
    private final StorageService storage;
    private final RecipeService recipes;
    private final ResourceNodeService nodes;
    private final PowerNetworkService power;
    private final int maxPerCycle;
    private BukkitTask task;

    public MachineTickService(JavaPlugin plugin, MachineService machines, MachineDefinitionService definitions, StorageService storage,
                              RecipeService recipes, ResourceNodeService nodes, PowerNetworkService power, int maxPerCycle) {
        this.plugin = plugin;
        this.machines = machines;
        this.definitions = definitions;
        this.storage = storage;
        this.recipes = recipes;
        this.nodes = nodes;
        this.power = power;
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
        if (definition.isGenerator()) {
            processGenerator(machine);
        } else if (machine.typeId().equals("harvester_t1")) {
            processHarvester(machine, definition);
        } else if (machine.typeId().equals("miner_drill_t1")) {
            processDrill(machine, definition);
        } else {
            processRecipe(machine);
        }
    }

    private void processGenerator(MachineInstance machine) {
        VirtualInventory input = storage.islandStorage(machine.islandUuid());
        if (!input.remove("biofuel", 1)) {
            setStatus(machine, MachineStatus.INPUT_MISSING);
            return;
        }
        storage.save(input);
        setStatus(machine, MachineStatus.RUNNING);
    }

    private void processHarvester(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory output = storage.islandStorage(machine.islandUuid());
        Location location = location(machine.location());
        if (location == null) {
            return;
        }
        int harvested = 0;
        int range = Math.max(1, definition.range());
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                Block block = location.clone().add(x, 0, z).getBlock();
                if (block.getType() == Material.WHEAT && block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
                    if (!output.add("wheat", 1)) {
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

    private void processDrill(MachineInstance machine, MachineDefinition definition) {
        VirtualInventory output = storage.islandStorage(machine.islandUuid());
        Optional<ResourceNode> node = machine.linkedResourceNodeId() == null
                ? nodes.nearest(machine.islandUuid(), machine.location(), 12)
                : nodes.nodes(machine.islandUuid()).stream().filter(candidate -> candidate.nodeId().equals(machine.linkedResourceNodeId())).findFirst();
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
        VirtualInventory inventory = storage.islandStorage(machine.islandUuid());
        for (RecipeDefinition recipe : recipes.recipesFor(machine.typeId())) {
            if (recipe.input().entrySet().stream().allMatch(entry -> inventory.amount(entry.getKey()) >= entry.getValue())
                    && recipe.output().entrySet().stream().allMatch(entry -> inventory.canAdd(entry.getKey(), entry.getValue()))) {
                recipe.input().forEach(inventory::remove);
                recipe.output().forEach(inventory::add);
                storage.save(inventory);
                setStatus(machine, MachineStatus.RUNNING);
                return;
            }
        }
        setStatus(machine, MachineStatus.INPUT_MISSING);
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
