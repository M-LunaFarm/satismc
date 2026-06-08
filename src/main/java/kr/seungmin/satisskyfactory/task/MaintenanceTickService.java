package kr.seungmin.satisskyfactory.task;

import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class MaintenanceTickService {
    private final JavaPlugin plugin;
    private final FactoryIslandService islands;
    private final SuperiorSkyblockHook skyblock;
    private final MaintenanceService maintenance;
    private BukkitTask task;

    public MaintenanceTickService(JavaPlugin plugin, FactoryIslandService islands, SuperiorSkyblockHook skyblock,
                                  MaintenanceService maintenance) {
        this.plugin = plugin;
        this.islands = islands;
        this.skyblock = skyblock;
        this.maintenance = maintenance;
    }

    public void start(long intervalTicks) {
        stop();
        long period = Math.max(1L, intervalTicks);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (FactoryIsland island : islands.cached()) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(island.ownerUuid());
            Object rawIsland = skyblock.getIslandByUuid(island.islandUuid())
                    .map(SuperiorSkyblockHook.IslandRef::raw)
                    .orElse(null);
            maintenance.chargeIfDue(island, owner, rawIsland);
            islands.save(island);
        }
    }
}
