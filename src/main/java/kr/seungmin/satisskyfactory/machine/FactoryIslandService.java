package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactoryIslandService {
    private final SuperiorSkyblockHook skyblockHook;
    private final DatabaseService database;
    private final Map<UUID, FactoryIsland> cache = new ConcurrentHashMap<>();

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

    public FactoryIsland getOrCreate(SuperiorSkyblockHook.IslandRef island) {
        return cache.computeIfAbsent(island.islandUuid(), uuid -> {
            FactoryIsland loaded = database.findIsland(uuid).orElseGet(() -> new FactoryIsland(uuid, island.ownerUuid()));
            loaded.ownerUuid(island.ownerUuid());
            database.saveIsland(loaded);
            return loaded;
        });
    }

    public void save(FactoryIsland island) {
        cache.put(island.islandUuid(), island);
        database.saveIsland(island);
    }
}
