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
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
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
    private final Set<String> recoveryTypes;
    private final IslandBoostService boosts;
    private final int baseMachineLimit;

    public MachineListener(CustomItemFactory itemFactory, MachineDefinitionService definitions, MachineService machines,
                           SuperiorSkyblockHook skyblock, FactoryIslandService islands, FactoryGuiService gui,
                           MessageService messages, FileConfiguration config, IslandBoostService boosts) {
        this.itemFactory = itemFactory;
        this.definitions = definitions;
        this.machines = machines;
        this.skyblock = skyblock;
        this.islands = islands;
        this.gui = gui;
        this.messages = messages;
        this.recoveryTypes = Set.copyOf(config.getStringList("limits.recovery-machine-types"));
        this.boosts = boosts;
        this.baseMachineLimit = config.getInt("limits.base-machines-per-island", 128);
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
            if (island.maintenanceStatus() == MaintenanceStatus.LOCKED && !recoveryTypes.contains(typeId)) {
                messages.send(player, "place-denied");
                return;
            }
            int machineLimit = boosts.boosts(islandRef.raw()).machineLimit(baseMachineLimit);
            if (machines.byIsland(island.islandUuid()).size() >= machineLimit) {
                messages.send(player, "place-denied");
                return;
            }
            event.setCancelled(false);
            MachineInstance machine = machines.create(island.islandUuid(), player.getUniqueId(), typeId, event.getBlockPlaced().getLocation(), player.getFacing());
            messages.send(player, "placed", Map.of("machine", definition.displayName()));
            islands.save(island);
            machines.save(machine);
        });
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
            machines.remove(machine);
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
