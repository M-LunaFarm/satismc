package kr.seungmin.satisskyfactory.node;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.model.ResourceNodeType;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import kr.seungmin.satisskyfactory.task.DirtySaveService;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ResourceNodeService {
    private final DatabaseService database;
    private final ConcurrentHashMap<UUID, List<ResourceNode>> nodesByIsland = new ConcurrentHashMap<>();
    private NodeGenerationService nodeGeneration;
    private DirtySaveService dirtySaves;
    private boolean regenerationEnabled = true;
    private long minimumRegenerationIntervalMillis = 1000L;

    public ResourceNodeService(DatabaseService database) {
        this.database = database;
    }

    public void load(FileConfiguration config) {
        this.nodeGeneration = new NodeGenerationService(config);
        regenerationEnabled = config.getBoolean("resource-nodes.regeneration.enabled",
                config.getBoolean("regeneration.enabled", true));
        minimumRegenerationIntervalMillis = Math.max(0L, config.getLong("resource-nodes.regeneration.minimum-interval-ms",
                config.getLong("regeneration.minimum-interval-ms", 1000L)));
    }

    public List<ResourceNode> nodes(UUID islandUuid) {
        List<ResourceNode> nodes = nodesByIsland.computeIfAbsent(islandUuid, database::loadNodes);
        List<ResourceNode> regenerated = nodes.stream().map(this::regenerate).toList();
        if (regenerated != nodes) {
            nodesByIsland.put(islandUuid, regenerated);
        }
        return List.copyOf(regenerated);
    }

    public List<ResourceNode> generateIfMissing(UUID islandUuid, Location origin) {
        return generateIfMissing(islandUuid, origin, location -> true);
    }

    public List<ResourceNode> generateIfMissing(UUID islandUuid, Location origin, Predicate<Location> insideIsland) {
        List<ResourceNode> existing = nodes(islandUuid);
        if (!existing.isEmpty()) {
            return existing;
        }
        if (nodeGeneration == null) {
            return existing;
        }
        BlockKey originKey = BlockKey.from(origin);
        for (ResourceNode node : nodeGeneration.generate(islandUuid, originKey,
                location -> insideIsland.test(new Location(origin.getWorld(), location.x(), location.y(), location.z())),
                System.currentTimeMillis())) {
            save(node);
        }
        return nodes(islandUuid);
    }

    public Optional<ResourceNode> nearest(UUID islandUuid, BlockKey location, int maxDistance) {
        return nearest(islandUuid, location, maxDistance, null);
    }

    public Optional<ResourceNode> nearest(UUID islandUuid, BlockKey location, int maxDistance, ResourceNodeType type) {
        return nodes(islandUuid).stream()
                .filter(node -> node.location().world().equals(location.world()))
                .filter(node -> type == null || type.matches(node.nodeType()))
                .filter(node -> distanceSquared(node.location(), location) <= maxDistance * maxDistance)
                .min(Comparator.comparingInt(node -> distanceSquared(node.location(), location)));
    }

    public void save(ResourceNode node) {
        node.updatedAt(System.currentTimeMillis());
        cache(node);
        if (dirtySaves != null) {
            dirtySaves.markNode(node);
            return;
        }
        database.saveNode(node);
    }

    public void dirtySaves(DirtySaveService dirtySaves) {
        this.dirtySaves = dirtySaves;
    }

    private void cache(ResourceNode node) {
        nodesByIsland.compute(node.islandUuid(), (islandUuid, current) -> {
            List<ResourceNode> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
            updated.removeIf(existing -> existing.nodeId().equals(node.nodeId()));
            updated.add(node);
            return updated;
        });
    }

    private ResourceNode regenerate(ResourceNode node) {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - node.updatedAt());
        if (!regenerationEnabled || node.remaining() >= node.maxRemaining() || node.regenPerHour() <= 0
                || elapsed < minimumRegenerationIntervalMillis) {
            return node;
        }
        long restored = Math.floorDiv(node.regenPerHour() * elapsed, 60L * 60L * 1000L);
        if (restored <= 0) {
            return node;
        }
        long before = node.remaining();
        node.remaining(Math.min(node.maxRemaining(), before + restored));
        node.updatedAt(now);
        if (node.remaining() != before) {
            save(node);
        }
        return node;
    }

    private int distanceSquared(BlockKey a, BlockKey b) {
        int dx = a.x() - b.x();
        int dy = a.y() - b.y();
        int dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

}
