package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceNodeServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void regeneratesStoredNodesFromElapsedTime() throws Exception {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000701");
        UUID nodeId = UUID.fromString("00000000-0000-0000-0000-000000000702");
        try (DatabaseHandle handle = openDatabase("regen-db")) {
            ResourceNode node = new ResourceNode(nodeId, islandUuid, "MINERAL", "iron_ore", 1.0,
                    100, 250, 60, 1, new BlockKey("world", 0, 64, 0), 0);
            handle.database().saveNode(node);
            setUpdatedAt(handle.database(), nodeId, Instant.now().minus(Duration.ofHours(2)).toEpochMilli());
            ResourceNodeService nodes = new ResourceNodeService(handle.database());
            nodes.load(config(true, 0));

            ResourceNode regenerated = nodes.nodes(islandUuid).stream()
                    .filter(candidate -> candidate.nodeId().equals(nodeId))
                    .findFirst()
                    .orElseThrow();

            assertEquals(220, regenerated.remaining());
            assertEquals(220, handle.database().loadNodes(islandUuid).stream()
                    .filter(candidate -> candidate.nodeId().equals(nodeId))
                    .findFirst()
                    .orElseThrow()
                    .remaining());
        }
    }

    @Test
    void regenerationCanBeDisabledByConfig() throws Exception {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000000711");
        UUID nodeId = UUID.fromString("00000000-0000-0000-0000-000000000712");
        try (DatabaseHandle handle = openDatabase("regen-disabled-db")) {
            ResourceNode node = new ResourceNode(nodeId, islandUuid, "FOREST", "wood_log", 1.0,
                    100, 250, 60, 1, new BlockKey("world", 0, 64, 0), 0);
            handle.database().saveNode(node);
            setUpdatedAt(handle.database(), nodeId, Instant.now().minus(Duration.ofHours(2)).toEpochMilli());
            ResourceNodeService nodes = new ResourceNodeService(handle.database());
            nodes.load(config(false, 0));

            ResourceNode unchanged = nodes.nodes(islandUuid).stream()
                    .filter(candidate -> candidate.nodeId().equals(nodeId))
                    .findFirst()
                    .orElseThrow();

            assertEquals(100, unchanged.remaining());
        }
    }

    private DatabaseHandle openDatabase(String name) {
        DatabaseService database = new DatabaseService(tempDir.resolve(name).toFile());
        database.open();
        return new DatabaseHandle(database);
    }

    private YamlConfiguration config(boolean enabled, long minimumIntervalMillis) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("resource-nodes.regeneration.enabled", enabled);
        config.set("resource-nodes.regeneration.minimum-interval-ms", minimumIntervalMillis);
        return config;
    }

    private void setUpdatedAt(DatabaseService database, UUID nodeId, long updatedAt) throws Exception {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("UPDATE resource_nodes SET updated_at = ? WHERE node_id = ?")) {
            statement.setLong(1, updatedAt);
            statement.setString(2, nodeId.toString());
            statement.executeUpdate();
        }
    }

    private record DatabaseHandle(DatabaseService database) implements AutoCloseable {
        @Override
        public void close() {
            database.close();
        }
    }
}
