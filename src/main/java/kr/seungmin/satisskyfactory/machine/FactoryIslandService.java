package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactoryIslandService {
    private final SuperiorSkyblockHook skyblockHook;
    private final DatabaseService database;
    private final Map<UUID, FactoryIsland> cache = new ConcurrentHashMap<>();
    private DirtySaveService dirtySaves;

    public FactoryIslandService(SuperiorSkyblockHook skyblockHook, DatabaseService database) {
        this.skyblockHook = skyblockHook;
        this.database = database;
    }

    public Optional<FactoryContext> context(Player player) {
        Optional<SuperiorSkyblockHook.IslandRef> island = skyblockHook.getIslandOf(player);
        if (island.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FactoryContext(island.get(), getOrCreate(island.get())));
    }

    public void load() {
        cache.clear();
        for (FactoryIsland island : database.loadIslands()) {
            cache.put(island.islandUuid(), island);
        }
    }

    public FactoryIsland getOrCreate(SuperiorSkyblockHook.IslandRef island) {
        return cache.computeIfAbsent(island.islandUuid(), uuid -> {
            FactoryIsland loaded = database.findIsland(uuid).orElseGet(() -> new FactoryIsland(uuid, island.ownerUuid()));
            loaded.ownerUuid(island.ownerUuid());
            database.saveIsland(loaded);
            return loaded;
        });
    }

    public Optional<FactoryIsland> find(UUID islandUuid) {
        FactoryIsland cached = cache.get(islandUuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<FactoryIsland> loaded = database.findIsland(islandUuid);
        loaded.ifPresent(island -> cache.put(island.islandUuid(), island));
        return loaded;
    }

    public Collection<FactoryIsland> cached() {
        return new ArrayList<>(cache.values());
    }

    public void save(FactoryIsland island) {
        cache.put(island.islandUuid(), island);
        if (dirtySaves != null) {
            dirtySaves.markIsland(island);
            return;
        }
        database.saveIsland(island);
    }

    public void dirtySaves(DirtySaveService dirtySaves) {
        this.dirtySaves = dirtySaves;
    }
}
