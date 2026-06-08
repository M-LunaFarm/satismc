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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class ResourceNodeService {
    private final DatabaseService database;
    private final ConcurrentHashMap<UUID, List<ResourceNode>> nodesByIsland = new ConcurrentHashMap<>();
    private FileConfiguration config;
    private DirtySaveService dirtySaves;

    public ResourceNodeService(DatabaseService database) {
        this.database = database;
    }

    public void load(FileConfiguration config) {
        this.config = config;
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
        if (config.isList("resource-nodes.default-new-island-nodes")) {
            generateFromGoalList(islandUuid, origin, insideIsland);
            return nodes(islandUuid);
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
            save(node);
        }
        return nodes(islandUuid);
    }

    private void generateFromGoalList(UUID islandUuid, Location origin, Predicate<Location> insideIsland) {
        List<Map<?, ?>> nodeConfigs = config.getMapList("resource-nodes.default-new-island-nodes");
        int count = Math.min(config.getInt("resource-nodes.nodes-per-island", nodeConfigs.size()), nodeConfigs.size());
        for (int i = 0; i < count; i++) {
            Map<?, ?> nodeConfig = nodeConfigs.get(i);
            Location location = origin.clone().add(
                    intValue(nodeConfig, "offset-x", 6 + i * 4),
                    intValue(nodeConfig, "offset-y", 0),
                    intValue(nodeConfig, "offset-z", 4 + i * 3)
            );
            if (!insideIsland.test(location)) {
                if (!insideIsland.test(origin)) {
                    continue;
                }
                location = origin.clone();
            }
            double purity = purity(nodeConfig);
            long maxRemaining = longValue(nodeConfig, "max-remaining", longValue(nodeConfig, "remaining", 100000L));
            long now = System.currentTimeMillis();
            save(new ResourceNode(
                    UUID.randomUUID(),
                    islandUuid,
                    stringValue(nodeConfig, "node-type", stringValue(nodeConfig, "type", "MINERAL")),
                    stringValue(nodeConfig, "resource-id", "iron_ore"),
                    purity,
                    maxRemaining,
                    maxRemaining,
                    longValue(nodeConfig, "regen-per-hour", 100L),
                    intValue(nodeConfig, "required-machine-tier", 1),
                    BlockKey.from(location),
                    now
            ));
        }
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

    private double purity(Map<?, ?> nodeConfig) {
        if (nodeConfig.containsKey("purity")) {
            return doubleValue(nodeConfig, "purity", 1.0);
        }
        double min = doubleValue(nodeConfig, "purity-min", 1.0);
        double max = doubleValue(nodeConfig, "purity-max", min);
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

    private String stringValue(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int intValue(Map<?, ?> values, String key, int fallback) {
        return (int) longValue(values, key, fallback);
    }

    private long longValue(Map<?, ?> values, String key, long fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double doubleValue(Map<?, ?> values, String key, double fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
