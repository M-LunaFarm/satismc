package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FactoryIslandServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void getOrCreateSynchronizesCachedOwnerWithoutLosingIslandState() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000005001");
        UUID originalOwner = UUID.fromString("00000000-0000-0000-0000-000000005002");
        UUID transferredOwner = UUID.fromString("00000000-0000-0000-0000-000000005003");

        try (DatabaseHandle handle = openDatabase("owner-sync")) {
            FactoryIslandService islands = new FactoryIslandService(null, handle.database());
            FactoryIsland island = islands.getOrCreate(islandRef(islandUuid, originalOwner));
            island.researchPoints(75);
            islands.save(island);

            FactoryIsland transferred = islands.getOrCreate(islandRef(islandUuid, transferredOwner));

            assertSame(island, transferred);
            assertEquals(transferredOwner, transferred.ownerUuid());
            assertEquals(75, transferred.researchPoints());
            FactoryIsland persisted = handle.database().findIsland(islandUuid).orElseThrow();
            assertEquals(transferredOwner, persisted.ownerUuid());
            assertEquals(75, persisted.researchPoints());
        }
    }

    private SuperiorSkyblockHook.IslandRef islandRef(UUID islandUuid, UUID ownerUuid) {
        return new SuperiorSkyblockHook.IslandRef(null, islandUuid, ownerUuid);
    }

    private DatabaseHandle openDatabase(String name) {
        DatabaseService database = new DatabaseService(tempDir.resolve(name).toFile(), "data.db");
        database.open();
        return new DatabaseHandle(database);
    }

    private record DatabaseHandle(DatabaseService database) implements AutoCloseable {
        @Override
        public void close() {
            database.close();
        }
    }
}
