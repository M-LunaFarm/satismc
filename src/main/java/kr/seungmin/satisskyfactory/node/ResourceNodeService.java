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
            Location location = origin.clone().add(6 + i * 4, 0, 4 + i * 3);
            long now = System.currentTimeMillis();
            ResourceNode node = new ResourceNode(
                    UUID.randomUUID(),
                    islandUuid,
                    config.getString("nodes." + nodeKey + ".type", "MINERAL"),
                    config.getString("nodes." + nodeKey + ".resource-id", "iron_ore"),
                    config.getDouble("nodes." + nodeKey + ".purity", 1.0),
                    config.getLong("nodes." + nodeKey + ".remaining", 100000),
                    config.getLong("nodes." + nodeKey + ".remaining", 100000),
                    config.getLong("nodes." + nodeKey + ".regen-per-hour", 100),
                    config.getInt("nodes." + nodeKey + ".required-machine-tier", 1),
                    BlockKey.from(location),
                    now
            );
            database.saveNode(node);
        }
        return nodes(islandUuid);
    }

    public Optional<ResourceNode> nearest(UUID islandUuid, BlockKey location, int maxDistance) {
        return nearest(islandUuid, location, maxDistance, null);
    }

    public Optional<ResourceNode> nearest(UUID islandUuid, BlockKey location, int maxDistance, ResourceNodeType type) {
        return nodes(islandUuid).stream()
                .filter(node -> node.location().world().equals(location.world()))
                .filter(node -> type == null || node.nodeType().equalsIgnoreCase(type.name()))
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
