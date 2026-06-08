package kr.seungmin.satisskyfactory.listener;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.logistics.ItemNetworkService;
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
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;

public final class MachineListener implements Listener {
    private final JavaPlugin plugin;
    private final CustomItemFactory itemFactory;
    private final MachineDefinitionService definitions;
    private final MachineService machines;
    private final SuperiorSkyblockHook skyblock;
    private final FactoryIslandService islands;
    private final FactoryGuiService gui;
    private final MessageService messages;
    private final ResearchService research;
    private final ResourceNodeService nodes;
    private final ItemNetworkService itemNetworks;
    private final PowerNetworkService power;
    private final FileConfiguration config;
    private final FileConfiguration maintenanceConfig;
    private final IslandBoostService boosts;

    public MachineListener(JavaPlugin plugin, CustomItemFactory itemFactory, MachineDefinitionService definitions, MachineService machines,
                           SuperiorSkyblockHook skyblock, FactoryIslandService islands, FactoryGuiService gui,
                           MessageService messages, ResearchService research, ResourceNodeService nodes,
                           ItemNetworkService itemNetworks,
                           PowerNetworkService power,
                           FileConfiguration config, FileConfiguration maintenanceConfig, IslandBoostService boosts) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.definitions = definitions;
        this.machines = machines;
        this.skyblock = skyblock;
        this.islands = islands;
        this.gui = gui;
        this.messages = messages;
        this.research = research;
        this.nodes = nodes;
        this.itemNetworks = itemNetworks;
        this.power = power;
        this.config = config;
        this.maintenanceConfig = maintenanceConfig;
        this.boosts = boosts;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        itemFactory.machineType(event.getItemInHand()).ifPresent(typeId -> {
            event.setCancelled(true);
            Block targetBlock = event.getBlockPlaced();
            BlockFace direction = player.getFacing();
            EquipmentSlot hand = event.getHand();
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> installMachine(player, typeId, targetBlock, direction, true, hand, false));
        });
    }

    private boolean installMachine(Player player, String typeId, Block targetBlock, BlockFace direction,
                                   boolean consumeItem, EquipmentSlot hand, boolean replacePlacedBlock) {
        MachineDefinition definition = definitions.get(typeId).orElse(null);
        if (definition == null) {
            messages.send(player, "unknown-machine");
            return false;
        }
        if (consumeItem && player.getGameMode() != GameMode.CREATIVE && !hasInstallItem(player, hand, typeId)) {
            messages.send(player, "hold-item-first");
            return false;
        }
        if (!replacePlacedBlock && !targetBlock.getType().isAir()) {
            messages.send(player, "place-denied");
            return false;
        }
        if (machines.at(targetBlock.getLocation()).isPresent()) {
            messages.send(player, "place-denied");
            return false;
        }
        if (!skyblock.canBuildFactory(player, targetBlock.getLocation())) {
            messages.send(player, "place-denied");
            return false;
        }
        SuperiorSkyblockHook.IslandRef islandRef = skyblock.getIslandAt(targetBlock.getLocation()).orElse(null);
        if (islandRef == null) {
            messages.send(player, "no-island");
            return false;
        }
        FactoryIsland island = islands.getOrCreate(islandRef);
        if (definition.tier() > island.tier() || !research.unlocked(island).containsAll(definition.requiredUnlocks())) {
            messages.send(player, "place-denied");
            return false;
        }
        if (island.maintenanceStatus() == MaintenanceStatus.LIMITED && limitedBlocksNewMachines()) {
            messages.send(player, "place-denied");
            return false;
        }
        if (island.maintenanceStatus() == MaintenanceStatus.LOCKED
                && (!lockedAllowsRecoveryMachines() || !recoveryTypes().contains(typeId))) {
            messages.send(player, "place-denied");
            return false;
        }
        int machineLimit = boosts.boosts(islandRef.raw()).machineLimit(baseMachineLimit()
                + Math.max(0, island.tier() - 1) * machineLimitPerIslandTier());
        if (machines.byIsland(island.islandUuid()).size() >= machineLimit) {
            messages.send(player, "place-denied");
            return false;
        }
        targetBlock.setType(definition.placedMaterial(), false);
        MachineInstance machine = machines.create(island.islandUuid(), player.getUniqueId(), typeId, targetBlock.getLocation(), direction);
        linkResourceNode(machine, definition);
        if (consumeItem && player.getGameMode() != GameMode.CREATIVE) {
            consumeHand(player, hand);
        }
        messages.send(player, "placed", Map.of("machine", definition.displayName()));
        islands.save(island);
        machines.save(machine);
        itemNetworks.rebuildIsland(island.islandUuid());
        power.rebuildIsland(island.islandUuid());
        return true;
    }

    private boolean hasInstallItem(Player player, EquipmentSlot hand, String typeId) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        return itemFactory.machineType(item)
                .filter(typeId::equals)
                .isPresent();
    }

    private void consumeHand(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        int amount = item.getAmount();
        if (amount <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        item.setAmount(amount - 1);
    }

    private void linkResourceNode(MachineInstance machine, MachineDefinition definition) {
        if (definition.nodeType() == null) {
            return;
        }
        nodes.nearest(machine.islandUuid(), machine.location(), nodeLinkRadius(), definition.nodeType())
                .ifPresentOrElse(node -> machine.linkedResourceNodeId(node.nodeId()), () -> machine.status(MachineStatus.INPUT_MISSING));
    }

    private Set<String> recoveryTypes() {
        return Set.copyOf(config.getStringList("limits.recovery-machine-types"));
    }

    private int baseMachineLimit() {
        return config.contains("limits.base-machine-limit")
                ? config.getInt("limits.base-machine-limit", 128)
                : config.getInt("limits.base-machines-per-island", 128);
    }

    private int machineLimitPerIslandTier() {
        return Math.max(0, config.getInt("limits.machine-limit-per-island-tier", 0));
    }

    private int nodeLinkRadius() {
        return Math.max(1, config.contains("resource-nodes.link-radius")
                ? config.getInt("resource-nodes.link-radius", 3)
                : config.getInt("settings.resource-node-link-radius", 3));
    }

    private boolean limitedBlocksNewMachines() {
        return maintenanceConfig.getBoolean("maintenance.limited.block-new-machine-placement", true);
    }

    private boolean lockedAllowsRecoveryMachines() {
        return maintenanceConfig.getBoolean("maintenance.locked.allow-basic-recovery-machines", true);
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
            if (!machines.remove(machine)) {
                event.setCancelled(true);
                messages.send(player, "machine-remove-storage-full");
                return;
            }
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (definition != null) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), itemFactory.machineItem(definition, 1));
                messages.send(player, "removed", Map.of("machine", definition.displayName()));
            }
            itemNetworks.rebuildIsland(machine.islandUuid());
            power.rebuildIsland(machine.islandUuid());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            itemFactory.machineType(event.getItem()).ifPresent(typeId -> {
                event.setCancelled(true);
                Block target = event.getClickedBlock().getRelative(event.getBlockFace());
                installMachine(event.getPlayer(), typeId, target, event.getBlockFace(), true, event.getHand(), false);
            });
            if (event.isCancelled()) {
                return;
            }
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
