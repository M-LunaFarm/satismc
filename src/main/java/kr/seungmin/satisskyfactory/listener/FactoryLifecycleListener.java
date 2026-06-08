package kr.seungmin.satisskyfactory.listener;

import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.logistics.ItemNetworkService;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public final class FactoryLifecycleListener implements Listener {
    private final FactoryIslandService islands;
    private final SuperiorSkyblockHook skyblock;
    private final ResourceNodeService nodes;
    private final MachineService machines;
    private final ItemNetworkService itemNetworks;
    private final PowerNetworkService power;

    public FactoryLifecycleListener(FactoryIslandService islands, SuperiorSkyblockHook skyblock,
                                    ResourceNodeService nodes, MachineService machines, ItemNetworkService itemNetworks,
                                    PowerNetworkService power) {
        this.islands = islands;
        this.skyblock = skyblock;
        this.nodes = nodes;
        this.machines = machines;
        this.itemNetworks = itemNetworks;
        this.power = power;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        skyblock.getIslandOf(event.getPlayer()).ifPresent(islandRef -> {
            FactoryIsland island = islands.getOrCreate(islandRef);
            island.lastTickAt(System.currentTimeMillis());
            Location origin = skyblock.getIslandCenter(islandRef).orElse(event.getPlayer().getLocation());
            nodes.generateIfMissing(island.islandUuid(), origin, location -> skyblock.getIslandAt(location)
                    .map(ref -> ref.islandUuid().equals(island.islandUuid()))
                    .orElse(false));
            itemNetworks.rebuildIsland(island.islandUuid());
            power.rebuildIsland(island.islandUuid());
            islands.save(island);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        islands.context(event.getPlayer()).ifPresent(context -> {
            context.factoryIsland().lastTickAt(System.currentTimeMillis());
            islands.save(context.factoryIsland());
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        machines.markChunkStatus(event.getChunk(), MachineStatus.CHUNK_UNLOADED);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (MachineInstance machine : machines.byChunk(event.getChunk())) {
            if (machine.status() == MachineStatus.CHUNK_UNLOADED) {
                machine.status(MachineStatus.IDLE);
                machines.saveLater(machine);
                itemNetworks.rebuildIsland(machine.islandUuid());
                power.rebuildIsland(machine.islandUuid());
            }
        }
    }
}
