package kr.seungmin.satisskyfactory.util;

import kr.seungmin.satisskyfactory.model.BlockKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationKeyTest {
    @Test
    void convertsBlockKeysWithoutLosingDatabaseShape() {
        BlockKey blockKey = new BlockKey("world_nether", -17, 64, 33);

        LocationKey locationKey = LocationKey.from(blockKey);

        assertEquals("world_nether", locationKey.world());
        assertEquals(-17, locationKey.x());
        assertEquals(64, locationKey.y());
        assertEquals(33, locationKey.z());
        assertEquals("world_nether:-17:64:33", locationKey.databaseKey());
        assertEquals(blockKey, locationKey.toBlockKey());
    }

    @Test
    void keepsChunkAndNeighborMathStable() {
        LocationKey locationKey = new LocationKey("world", 31, 70, -1);

        assertEquals(1, locationKey.chunkX());
        assertEquals(-1, locationKey.chunkZ());
        assertEquals(new LocationKey("world", 32, 68, 4), locationKey.relative(1, -2, 5));
    }
}
