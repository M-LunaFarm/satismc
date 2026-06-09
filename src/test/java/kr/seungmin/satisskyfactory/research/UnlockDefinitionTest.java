package kr.seungmin.satisskyfactory.research;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnlockDefinitionTest {
    @Test
    void storesConfiguredResearchFields() {
        UnlockDefinition definition = new UnlockDefinition(
                "tier_2",
                "공장 티어 2",
                100,
                100000,
                30,
                List.of("starter"),
                List.of("harvester_t2", "miner_drill_t2", "conveyor_t2"),
                2
        );

        assertEquals("tier_2", definition.id());
        assertEquals("공장 티어 2", definition.displayName());
        assertEquals(100, definition.cost());
        assertEquals(100000, definition.moneyCost());
        assertEquals(30, definition.requiredReputation());
        assertEquals(List.of("starter"), definition.requires());
        assertEquals(List.of("harvester_t2", "miner_drill_t2", "conveyor_t2"), definition.grants());
        assertEquals(2, definition.factoryTier());
    }
}
