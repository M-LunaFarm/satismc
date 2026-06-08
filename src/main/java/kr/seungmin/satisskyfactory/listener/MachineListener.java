package kr.seungmin.satisskyfactory.listener;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.Set;

public final class MachineListener implements Listener {
    private final CustomItemFactory itemFactory;
    private final MachineDefinitionService definitions;
    private final MachineService machines;
    private final SuperiorSkyblockHook skyblock;
    private final FactoryIslandService islands;
    private final FactoryGuiService gui;
    private final MessageService messages;
    private final ResearchService research;
    private final ResourceNodeService nodes;
    private final Set<String> recoveryTypes;
    private final IslandBoostService boosts;
    private final int baseMachineLimit;
    private final int nodeLinkRadius;
    private final boolean limitedBlocksNewMachines;
    private final boolean lockedAllowsRecoveryMachines;

    public MachineListener(CustomItemFactory itemFactory, MachineDefinitionService definitions, MachineService machines,
                           SuperiorSkyblockHook skyblock, FactoryIslandService islands, FactoryGuiService gui,
                           MessageService messages, ResearchService research, ResourceNodeService nodes,
                           FileConfiguration config, FileConfiguration maintenanceConfig, IslandBoostService boosts) {
        this.itemFactory = itemFactory;
        this.definitions = definitions;
        this.machines = machines;
        this.skyblock = skyblock;
        this.islands = islands;
        this.gui = gui;
        this.messages = messages;
        this.research = research;
        this.nodes = nodes;
        this.recoveryTypes = Set.copyOf(config.getStringList("limits.recovery-machine-types"));
        this.boosts = boosts;
        this.baseMachineLimit = config.contains("limits.base-machine-limit")
                ? config.getInt("limits.base-machine-limit", 128)
                : config.getInt("limits.base-machines-per-island", 128);
        this.nodeLinkRadius = Math.max(1, config.contains("resource-nodes.link-radius")
                ? config.getInt("resource-nodes.link-radius", 3)
                : config.getInt("settings.resource-node-link-radius", 3));
        this.limitedBlocksNewMachines = maintenanceConfig.getBoolean("maintenance.limited.block-new-machine-placement", true);
        this.lockedAllowsRecoveryMachines = maintenanceConfig.getBoolean("maintenance.locked.allow-basic-recovery-machines", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        itemFactory.machineType(event.getItemInHand()).ifPresent(typeId -> {
            event.setCancelled(true);
            MachineDefinition definition = definitions.get(typeId).orElse(null);
            if (definition == null) {
                messages.send(player, "unknown-machine");
                return;
            }
            if (!skyblock.canBuildFactory(player, event.getBlockPlaced().getLocation())) {
                messages.send(player, "place-denied");
                return;
            }
            SuperiorSkyblockHook.IslandRef islandRef = skyblock.getIslandAt(event.getBlockPlaced().getLocation()).orElse(null);
            if (islandRef == null) {
                messages.send(player, "no-island");
                return;
            }
            FactoryIsland island = islands.getOrCreate(islandRef);
            if (definition.tier() > island.tier() || !research.unlocked(island).containsAll(definition.requiredUnlocks())) {
                messages.send(player, "place-denied");
                return;
            }
            if (island.maintenanceStatus() == MaintenanceStatus.LIMITED && limitedBlocksNewMachines) {
                messages.send(player, "place-denied");
                return;
            }
            if (island.maintenanceStatus() == MaintenanceStatus.LOCKED
                    && (!lockedAllowsRecoveryMachines || !recoveryTypes.contains(typeId))) {
                messages.send(player, "place-denied");
                return;
            }
            int machineLimit = boosts.boosts(islandRef.raw()).machineLimit(baseMachineLimit);
            if (machines.byIsland(island.islandUuid()).size() >= machineLimit) {
                messages.send(player, "place-denied");
                return;
            }
            event.setCancelled(false);
            event.getBlockPlaced().setType(definition.placedMaterial(), false);
            MachineInstance machine = machines.create(island.islandUuid(), player.getUniqueId(), typeId, event.getBlockPlaced().getLocation(), player.getFacing());
            linkResourceNode(machine, definition);
            messages.send(player, "placed", Map.of("machine", definition.displayName()));
            islands.save(island);
            machines.save(machine);
        });
    }

    private void linkResourceNode(MachineInstance machine, MachineDefinition definition) {
        if (definition.nodeType() == null) {
            return;
        }
        nodes.nearest(machine.islandUuid(), machine.location(), nodeLinkRadius, definition.nodeType())
                .ifPresentOrElse(node -> machine.linkedResourceNodeId(node.nodeId()), () -> machine.status(MachineStatus.INPUT_MISSING));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        machines.at(event.getBlock().getLocation()).ifPresent(machine -> {
            Player player = event.getPlayer();
            SuperiorSkyblockHook.IslandRef island = skyblock.getIslandAt(event.getBlock().getLocation()).orElse(null);
            if (island == null || !skyblock.isPlayerIslandMember(player, island)) {
                event.setCancelled(true);
                messages.send(player, "not-member");
                return;
            }
            MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
            if (machines.hasBufferedItems(machine)) {
                event.setCancelled(true);
                player.sendMessage("Empty this machine through its GUI before breaking it.");
                return;
            }
            if (!machines.remove(machine)) {
                event.setCancelled(true);
                player.sendMessage("Factory storage is full. Empty some space before removing this machine.");
                return;
            }
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (definition != null) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), itemFactory.machineItem(definition, 1));
                messages.send(player, "removed", Map.of("machine", definition.displayName()));
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        machines.at(event.getClickedBlock().getLocation()).ifPresent(machine -> {
            Player player = event.getPlayer();
            SuperiorSkyblockHook.IslandRef island = skyblock.getIslandAt(event.getClickedBlock().getLocation()).orElse(null);
            if (island == null || !skyblock.isPlayerIslandMember(player, island)) {
                event.setCancelled(true);
                messages.send(player, "not-member");
                return;
            }
            event.setCancelled(true);
            gui.openMachine(player, machine);
        });
    }
}
