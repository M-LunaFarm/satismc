package kr.seungmin.satisskyfactory.hook;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SuperiorSkyblockHookApiTest {
    @Test
    void exposesGoalIslandUuidAccessors() {
        SuperiorSkyblockHook hook = new SuperiorSkyblockHook(null);
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000006001");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000006002");
        SuperiorSkyblockHook.IslandRef island = new SuperiorSkyblockHook.IslandRef(new Object(), islandUuid, ownerUuid);

        assertEquals(islandUuid, hook.getIslandUuid(island));
        assertEquals(ownerUuid, hook.getIslandOwnerUuid(island));
    }
}
