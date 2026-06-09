package kr.seungmin.satisskyfactory.node;

import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class NodeGenerationService {
    private final FileConfiguration config;

    public NodeGenerationService(FileConfiguration config) {
        this.config = config;
    }

    public List<ResourceNode> generate(UUID islandUuid, BlockKey origin, Predicate<BlockKey> insideIsland, long now) {
        if (config.isList("resource-nodes.default-new-island-nodes")) {
            return generateFromGoalList(islandUuid, origin, insideIsland, now);
        }
        return generateFromLegacyNodes(islandUuid, origin, insideIsland, now);
    }

    private List<ResourceNode> generateFromGoalList(UUID islandUuid, BlockKey origin, Predicate<BlockKey> insideIsland, long now) {
        List<Map<?, ?>> nodeConfigs = config.getMapList("resource-nodes.default-new-island-nodes");
        int count = Math.min(config.getInt("resource-nodes.nodes-per-island", nodeConfigs.size()), nodeConfigs.size());
        List<ResourceNode> generated = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<?, ?> nodeConfig = nodeConfigs.get(i);
            BlockKey location = validLocation(origin.relative(
                    intValue(nodeConfig, "offset-x", 6 + i * 4),
                    intValue(nodeConfig, "offset-y", 0),
                    intValue(nodeConfig, "offset-z", 4 + i * 3)
            ), origin, insideIsland);
            if (location == null) {
                continue;
            }
            generated.add(new ResourceNode(
                    UUID.randomUUID(),
                    islandUuid,
                    stringValue(nodeConfig, "node-type", stringValue(nodeConfig, "type", "MINERAL")),
                    stringValue(nodeConfig, "resource-id", "iron_ore"),
                    purity(nodeConfig),
                    longValue(nodeConfig, "max-remaining", longValue(nodeConfig, "remaining", 100000L)),
                    longValue(nodeConfig, "max-remaining", longValue(nodeConfig, "remaining", 100000L)),
                    longValue(nodeConfig, "regen-per-hour", 100L),
                    intValue(nodeConfig, "required-machine-tier", 1),
                    location,
                    now,
                    now
            ));
        }
        return generated;
    }

    private List<ResourceNode> generateFromLegacyNodes(UUID islandUuid, BlockKey origin, Predicate<BlockKey> insideIsland, long now) {
        ConfigurationSection nodeSection = config.getConfigurationSection("nodes");
        if (nodeSection == null) {
            return List.of();
        }
        List<String> nodeKeys = new ArrayList<>(nodeSection.getKeys(false));
        int count = Math.min(config.getInt("defaults.nodes-per-island", nodeKeys.size()), nodeKeys.size());
        List<ResourceNode> generated = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String nodeKey = nodeKeys.get(i);
            String path = "nodes." + nodeKey + ".";
            BlockKey location = validLocation(origin.relative(
                    config.getInt(path + "offset-x", 6 + i * 4),
                    config.getInt(path + "offset-y", 0),
                    config.getInt(path + "offset-z", 4 + i * 3)
            ), origin, insideIsland);
            if (location == null) {
                continue;
            }
            long maxRemaining = config.getLong(path + "max-remaining", config.getLong(path + "remaining", 100000));
            generated.add(new ResourceNode(
                    UUID.randomUUID(),
                    islandUuid,
                    config.getString(path + "type", "MINERAL"),
                    config.getString(path + "resource-id", "iron_ore"),
                    purity(path),
                    maxRemaining,
                    maxRemaining,
                    config.getLong(path + "regen-per-hour", 100),
                    config.getInt(path + "required-machine-tier", 1),
                    location,
                    now,
                    now
            ));
        }
        return generated;
    }

    private BlockKey validLocation(BlockKey preferred, BlockKey origin, Predicate<BlockKey> insideIsland) {
        if (insideIsland.test(preferred)) {
            return preferred;
        }
        return insideIsland.test(origin) ? origin : null;
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
