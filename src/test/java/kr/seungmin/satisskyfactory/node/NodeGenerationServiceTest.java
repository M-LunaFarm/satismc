package kr.seungmin.satisskyfactory.node;

import kr.seungmin.satisskyfactory.model.BlockKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeGenerationServiceTest {
    @Test
    void generatesGoalDefaultNodesFromOffsets() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("resource-nodes.nodes-per-island", 2);
        config.set("resource-nodes.default-new-island-nodes", List.of(
                Map.of(
                        "node-type", "ORE",
                        "resource-id", "iron_ore",
                        "purity", 0.75,
                        "max-remaining", 12000,
                        "regen-per-hour", 300,
                        "required-machine-tier", 1,
                        "offset-x", 8,
                        "offset-y", 0,
                        "offset-z", 8
                ),
                Map.of(
                        "node-type", "FOREST",
                        "resource-id", "wood_log",
                        "purity", 0.5,
                        "max-remaining", 8000,
                        "regen-per-hour", 250,
                        "required-machine-tier", 1,
                        "offset-x", -8,
                        "offset-y", 0,
                        "offset-z", 8
                )
        ));

        var nodes = new NodeGenerationService(config).generate(
                UUID.fromString("00000000-0000-0000-0000-000000004001"),
                new BlockKey("world", 0, 64, 0),
                location -> true,
                1234L
        );

        assertEquals(2, nodes.size());
        assertEquals("ORE", nodes.get(0).nodeType());
        assertEquals("iron_ore", nodes.get(0).resourceId());
        assertEquals(new BlockKey("world", 8, 64, 8), nodes.get(0).location());
        assertEquals(12000, nodes.get(0).maxRemaining());
        assertEquals(300, nodes.get(0).regenPerHour());
        assertEquals("FOREST", nodes.get(1).nodeType());
        assertEquals("wood_log", nodes.get(1).resourceId());
        assertEquals(new BlockKey("world", -8, 64, 8), nodes.get(1).location());
        assertEquals(8000, nodes.get(1).maxRemaining());
        assertEquals(250, nodes.get(1).regenPerHour());
    }

    @Test
    void fallsBackToOriginWhenOffsetIsOutsideIsland() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("resource-nodes.default-new-island-nodes", List.of(Map.of(
                "node-type", "ORE",
                "resource-id", "iron_ore",
                "purity", 1.0,
                "max-remaining", 100,
                "offset-x", 8
        )));

        BlockKey origin = new BlockKey("world", 0, 64, 0);
        var nodes = new NodeGenerationService(config).generate(
                UUID.fromString("00000000-0000-0000-0000-000000004011"),
                origin,
                location -> location.equals(origin),
                1234L
        );

        assertEquals(origin, nodes.getFirst().location());
    }

    @Test
    void keepsLegacyNodeConfigurationSupport() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("defaults.nodes-per-island", 1);
        config.set("nodes.iron.type", "MINERAL");
        config.set("nodes.iron.resource-id", "iron_ore");
        config.set("nodes.iron.purity-min", 0.9);
        config.set("nodes.iron.purity-max", 0.9);
        config.set("nodes.iron.remaining", 5000);
        config.set("nodes.iron.regen-per-hour", 150);
        config.set("nodes.iron.required-machine-tier", 2);
        config.set("nodes.iron.offset-x", 4);
        config.set("nodes.iron.offset-y", 1);
        config.set("nodes.iron.offset-z", -4);

        var nodes = new NodeGenerationService(config).generate(
                UUID.fromString("00000000-0000-0000-0000-000000004021"),
                new BlockKey("world", 10, 64, 20),
                location -> true,
                1234L
        );

        assertEquals(1, nodes.size());
        assertEquals("MINERAL", nodes.getFirst().nodeType());
        assertEquals("iron_ore", nodes.getFirst().resourceId());
        assertEquals(new BlockKey("world", 14, 65, 16), nodes.getFirst().location());
        assertEquals(5000, nodes.getFirst().maxRemaining());
        assertEquals(150, nodes.getFirst().regenPerHour());
        assertEquals(2, nodes.getFirst().requiredMachineTier());
        assertEquals(0.9, nodes.getFirst().purity());
    }
}
