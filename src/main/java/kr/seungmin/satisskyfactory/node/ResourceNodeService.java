package kr.seungmin.satisskyfactory.node;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.model.ResourceNodeType;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import kr.seungmin.satisskyfactory.task.DirtySaveService;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class ResourceNodeService {
    private final DatabaseService database;
    private FileConfiguration config;
    private DirtySaveService dirtySaves;

    public ResourceNodeService(DatabaseService database) {
        this.database = database;
    }

    public void load(FileConfiguration config) {
        this.config = config;
    }

    public List<ResourceNode> nodes(UUID islandUuid) {
        return database.loadNodes(islandUuid).stream().map(this::regenerate).toList();
    }

    public List<ResourceNode> generateIfMissing(UUID islandUuid, Location origin) {
        return generateIfMissing(islandUuid, origin, location -> true);
    }

    public List<ResourceNode> generateIfMissing(UUID islandUuid, Location origin, Predicate<Location> insideIsland) {
        List<ResourceNode> existing = nodes(islandUuid);
        if (!existing.isEmpty()) {
            return existing;
        }
        ConfigurationSection nodeSection = config.getConfigurationSection("nodes");
        if (nodeSection == null) {
            return existing;
        }
        List<String> nodeKeys = new ArrayList<>(nodeSection.getKeys(false));
        int count = Math.min(config.getInt("defaults.nodes-per-island", nodeKeys.size()), nodeKeys.size());
        for (int i = 0; i < count; i++) {
            String nodeKey = nodeKeys.get(i);
            String path = "nodes." + nodeKey + ".";
            Location location = origin.clone().add(
                    config.getInt(path + "offset-x", 6 + i * 4),
                    config.getInt(path + "offset-y", 0),
                    config.getInt(path + "offset-z", 4 + i * 3)
            );
            if (!insideIsland.test(location)) {
                if (!insideIsland.test(origin)) {
                    continue;
                }
                location = origin.clone();
            }
            double purity = purity(path);
            long maxRemaining = config.getLong(path + "max-remaining", config.getLong(path + "remaining", 100000));
            long now = System.currentTimeMillis();
            ResourceNode node = new ResourceNode(
                    UUID.randomUUID(),
                    islandUuid,
                    config.getString(path + "type", "MINERAL"),
                    config.getString(path + "resource-id", "iron_ore"),
                    purity,
                    maxRemaining,
                    maxRemaining,
                    config.getLong(path + "regen-per-hour", 100),
                    config.getInt(path + "required-machine-tier", 1),
                    BlockKey.from(location),
                    now
            );
            database.saveNode(node);
        }
        return nodes(islandUuid);
    }

    private double purity(String path) {
        if (config.contains(path + "purity")) {
            return config.getDouble(path + "purity", 1.0);
        }
        double min = config.getDouble(path + "purity-min", 1.0);
        double max = config.getDouble(path + "purity-max", min);
        if (max < min) {
            double swap = min;
            min = max;
            max = swap;
        }
        return min == max ? min : ThreadLocalRandom.current().nextDouble(min, max);
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
        if (dirtySaves != null) {
            dirtySaves.markNode(node);
            return;
        }
        database.saveNode(node);
    }

    public void dirtySaves(DirtySaveService dirtySaves) {
        this.dirtySaves = dirtySaves;
    }

    private ResourceNode regenerate(ResourceNode node) {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - node.updatedAt());
        if (node.remaining() >= node.maxRemaining() || node.regenPerHour() <= 0 || elapsed < 1000L) {
            return node;
        }
        long restored = Math.floorDiv(node.regenPerHour() * elapsed, 60L * 60L * 1000L);
        if (restored <= 0) {
            return node;
        }
        long before = node.remaining();
        node.remaining(before + restored);
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
