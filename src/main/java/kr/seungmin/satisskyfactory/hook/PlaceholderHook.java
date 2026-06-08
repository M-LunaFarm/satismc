package kr.seungmin.satisskyfactory.hook;

import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
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
    private final ContractService contracts;

    public PlaceholderHook(JavaPlugin plugin, FactoryIslandService islands, MachineService machines, StorageService storage,
                           PowerNetworkService power, IslandBoostService boosts, ResearchService research,
                           ContractService contracts) {
        this.plugin = plugin;
        this.islands = islands;
        this.machines = machines;
        this.storage = storage;
        this.power = power;
        this.boosts = boosts;
        this.research = research;
        this.contracts = contracts;
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
        VirtualInventory islandStorage = storage.islandStorage(island.islandUuid());
        PowerNetworkService.NetworkState powerState = power.state(island.islandUuid());
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
        if (key.equals("factory_score")) {
            return String.valueOf(machines.factoryScore(island.islandUuid()));
        }
        if (key.equals("maintenance_score")) {
            return String.valueOf(machines.maintenanceScore(island.islandUuid()));
        }
        if (key.equals("machines")) {
            return String.valueOf(machines.byIsland(island.islandUuid()).size());
        }
        if (key.equals("storage_used")) {
            return String.valueOf(islandStorage.used());
        }
        if (key.equals("storage_capacity")) {
            return String.valueOf(islandStorage.capacity());
        }
        if (key.equals("storage_free")) {
            return String.valueOf(Math.max(0, islandStorage.capacity() - islandStorage.used()));
        }
        if (key.equals("power_ratio")) {
            return String.format(Locale.US, "%.2f", powerState.ratio());
        }
        if (key.equals("power_generation")) {
            return String.format(Locale.US, "%.1f", powerState.generation());
        }
        if (key.equals("power_consumption")) {
            return String.format(Locale.US, "%.1f", powerState.consumption());
        }
        if (key.equals("battery_stored")) {
            return String.valueOf(powerState.batteryStored());
        }
        if (key.equals("battery_capacity")) {
            return String.format(Locale.US, "%.0f", powerState.batteryCapacity());
        }
        if (key.equals("battery_percent")) {
            return powerState.batteryCapacity() <= 0
                    ? "0"
                    : String.format(Locale.US, "%.0f", powerState.batteryStored() * 100.0 / powerState.batteryCapacity());
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
        if (key.equals("contracts_active")) {
            return String.valueOf(contracts.activeContracts(island).size());
        }
        if (key.startsWith("unlocked_")) {
            return String.valueOf(research.unlocked(island).contains(params.substring("unlocked_".length())));
        }
        return null;
    }
}
