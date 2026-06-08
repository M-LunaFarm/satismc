package kr.seungmin.satisskyfactory.research;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ResearchService {
    public record ResearchUnlock(String id, long cost, long moneyCost, long requiredReputation,
                                 List<String> requires, List<String> grants, int factoryTier) {
    }

    public enum UnlockResult {
        UNLOCKED,
        UNKNOWN,
        ALREADY_UNLOCKED,
        MISSING_REQUIREMENT,
        NOT_ENOUGH_POINTS,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_REPUTATION
    }

    private final DatabaseService database;
    private final EconomyService economy;
    private final Map<String, ResearchUnlock> unlocks = new HashMap<>();

    public ResearchService(DatabaseService database, EconomyService economy) {
        this.database = database;
        this.economy = economy;
    }

    public void load(FileConfiguration config) {
        unlocks.clear();
        ConfigurationSection section = config.getConfigurationSection("research.unlocks");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            unlocks.put(id, new ResearchUnlock(
                    id,
                    section.getLong(id + ".cost-research-points", section.getLong(id + ".cost", 0)),
                    section.getLong(id + ".cost-money", 0),
                    section.getLong(id + ".required-reputation", 0),
                    stringList(section, id + ".required-unlocks", id + ".requires"),
                    section.getStringList(id + ".unlocks"),
                    section.getInt(id + ".factory-tier", 0)
            ));
        }
    }

    public void addResearch(FactoryIsland island, long amount) {
        island.researchPoints(Math.max(0, island.researchPoints() + amount));
    }

    public UnlockResult unlock(FactoryIsland island, String unlockId) {
        return unlock(island, null, unlockId);
    }

    public UnlockResult unlock(FactoryIsland island, OfflinePlayer owner, String unlockId) {
        ResearchUnlock unlock = unlocks.get(unlockId);
        if (unlock == null) {
            return UnlockResult.UNKNOWN;
        }
        Set<String> current = database.loadUnlocks(island.islandUuid());
        if (current.contains(unlockId)) {
            return UnlockResult.ALREADY_UNLOCKED;
        }
        if (!current.containsAll(unlock.requires())) {
            return UnlockResult.MISSING_REQUIREMENT;
        }
        if (island.researchPoints() < unlock.cost()) {
            return UnlockResult.NOT_ENOUGH_POINTS;
        }
        if (island.reputation() < unlock.requiredReputation()) {
            return UnlockResult.NOT_ENOUGH_REPUTATION;
        }
        if (unlock.moneyCost() > 0 && (owner == null || !economy.withdraw(owner, unlock.moneyCost()))) {
            return UnlockResult.NOT_ENOUGH_MONEY;
        }
        island.researchPoints(island.researchPoints() - unlock.cost());
        if (unlock.factoryTier() > island.tier()) {
            island.tier(unlock.factoryTier());
        }
        database.saveUnlock(island.islandUuid(), unlockId);
        unlock.grants().forEach(grant -> database.saveUnlock(island.islandUuid(), grant));
        database.saveIsland(island);
        return UnlockResult.UNLOCKED;
    }

    public Set<String> unlocked(FactoryIsland island) {
        return database.loadUnlocks(island.islandUuid());
    }

    public Map<String, ResearchUnlock> all() {
        return Map.copyOf(unlocks);
    }

    private List<String> stringList(ConfigurationSection section, String firstPath, String secondPath) {
        List<String> values = new ArrayList<>(section.getStringList(firstPath));
        if (!values.isEmpty()) {
            return values;
        }
        values.addAll(section.getStringList(secondPath));
        return values;
    }
}
