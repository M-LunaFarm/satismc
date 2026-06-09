package kr.seungmin.satisskyfactory.contract;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractTest {
    @Test
    void protectsRequiredProgressAndRewardItemMaps() {
        Map<String, Long> required = new HashMap<>();
        required.put("bread_box", 8L);
        Map<String, Long> progress = new HashMap<>();
        progress.put("bread_box", 4L);
        Map<String, Long> rewardItems = new HashMap<>();
        rewardItems.put("machine_parts", 2L);

        Contract contract = new Contract(
                UUID.fromString("00000000-0000-0000-0000-000000006001"),
                UUID.fromString("00000000-0000-0000-0000-000000006002"),
                "bread_supply",
                1,
                Contract.Type.DAILY,
                required,
                new Contract.Rewards(1000, 5, 2, 0, rewardItems),
                progress,
                Contract.Status.ACTIVE,
                12345
        );

        required.put("bread_box", 99L);
        progress.put("bread_box", 99L);
        rewardItems.put("machine_parts", 99L);

        assertEquals(8, contract.requiredItems().get("bread_box"));
        assertEquals(4, contract.progress().get("bread_box"));
        assertEquals(2, contract.rewards().items().get("machine_parts"));
        assertThrows(UnsupportedOperationException.class, () -> contract.requiredItems().put("wheat", 1L));
    }

    @Test
    void completedCopyKeepsIdentityAndMarksProgress() {
        UUID contractId = UUID.fromString("00000000-0000-0000-0000-000000006101");
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000006102");
        Contract contract = new Contract(
                contractId,
                islandUuid,
                "daily_wheat",
                1,
                Contract.Type.DAILY,
                Map.of("wheat", 16L),
                new Contract.Rewards(100, 1, 0, 0, Map.of()),
                Map.of(),
                Contract.Status.ACTIVE,
                2000
        );

        Contract completed = contract.completed(Map.of("wheat", 16L));

        assertEquals(contractId, completed.contractId());
        assertEquals(islandUuid, completed.islandUuid());
        assertEquals(Contract.Status.COMPLETED, completed.status());
        assertEquals(16, completed.progress().get("wheat"));
    }
}
