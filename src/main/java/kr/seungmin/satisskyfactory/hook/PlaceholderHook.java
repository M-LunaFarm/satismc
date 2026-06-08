package kr.seungmin.satisskyfactory.hook;

import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Optional;

public final class PlaceholderHook extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final FactoryIslandService islands;
    private final MachineService machines;
    private final StorageService storage;
    private final PowerNetworkService power;
    private final IslandBoostService boosts;
    private final ResearchService research;

    public PlaceholderHook(JavaPlugin plugin, FactoryIslandService islands, MachineService machines, StorageService storage,
                           PowerNetworkService power, IslandBoostService boosts, ResearchService research) {
        this.plugin = plugin;
        this.islands = islands;
        this.machines = machines;
        this.storage = storage;
        this.power = power;
        this.boosts = boosts;
        this.research = research;
    }

    @Override
    public String getIdentifier() {
        return "satisskyfactory";
    }

    @Override
    public String getAuthor() {
        return "LeeSeungmin";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
        if (player == null) {
            return "";
        }
        Optional<FactoryContext> context = islands.context(player);
        if (context.isEmpty()) {
            return "";
        }
        FactoryIsland island = context.get().factoryIsland();
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("island_uuid")) {
            return island.islandUuid().toString();
        }
        if (key.equals("tier")) {
            return String.valueOf(island.tier());
        }
        if (key.equals("research")) {
            return String.valueOf(island.researchPoints());
        }
        if (key.equals("reputation")) {
            return String.valueOf(island.reputation());
        }
        if (key.equals("debt")) {
            return String.valueOf(island.maintenanceDebt());
        }
        if (key.equals("maintenance_status")) {
            return island.maintenanceStatus().name();
        }
        if (key.equals("machines")) {
            return String.valueOf(machines.byIsland(island.islandUuid()).size());
        }
        if (key.equals("storage_used")) {
            return String.valueOf(storage.islandStorage(island.islandUuid()).used());
        }
        if (key.equals("power_ratio")) {
            return String.format(Locale.US, "%.2f", power.powerRatio(island.islandUuid()));
        }
        if (key.equals("battery_stored")) {
            return String.valueOf(power.state(island.islandUuid()).batteryStored());
        }
        if (key.equals("battery_capacity")) {
            return String.format(Locale.US, "%.0f", power.state(island.islandUuid()).batteryCapacity());
        }
        if (key.equals("agriculture_boost")) {
            return String.format(Locale.US, "%.2f", boosts.boosts(island.islandUuid()).agricultureBoost());
        }
        if (key.equals("machine_limit_bonus")) {
            return String.valueOf(boosts.boosts(island.islandUuid()).factorySlotBonus());
        }
        if (key.equals("contract_slot_bonus")) {
            return String.valueOf(boosts.boosts(island.islandUuid()).contractSlotBonus());
        }
        if (key.startsWith("unlocked_")) {
            return String.valueOf(research.unlocked(island).contains(params.substring("unlocked_".length())));
        }
        return null;
    }
}
