package kr.seungmin.satisskyfactory.research;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ResearchService {
    public record ResearchUnlock(String id, long cost, List<String> requires) {
    }

    public enum UnlockResult {
        UNLOCKED,
        UNKNOWN,
        ALREADY_UNLOCKED,
        MISSING_REQUIREMENT,
        NOT_ENOUGH_POINTS
    }

    private final DatabaseService database;
    private final Map<String, ResearchUnlock> unlocks = new HashMap<>();

    public ResearchService(DatabaseService database) {
        this.database = database;
    }

    public void load(FileConfiguration config) {
        unlocks.clear();
        ConfigurationSection section = config.getConfigurationSection("research.unlocks");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            unlocks.put(id, new ResearchUnlock(id, section.getLong(id + ".cost", 0), section.getStringList(id + ".requires")));
        }
    }

    public void addResearch(FactoryIsland island, long amount) {
        island.researchPoints(Math.max(0, island.researchPoints() + amount));
    }

    public UnlockResult unlock(FactoryIsland island, String unlockId) {
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
        island.researchPoints(island.researchPoints() - unlock.cost());
        database.saveUnlock(island.islandUuid(), unlockId);
        database.saveIsland(island);
        return UnlockResult.UNLOCKED;
    }

    public Set<String> unlocked(FactoryIsland island) {
        return database.loadUnlocks(island.islandUuid());
    }

    public Map<String, ResearchUnlock> all() {
        return Map.copyOf(unlocks);
    }
}
