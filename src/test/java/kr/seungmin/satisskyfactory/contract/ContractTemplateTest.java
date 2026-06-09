package kr.seungmin.satisskyfactory.contract;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractTemplateTest {
    @Test
    void storesConfiguredContractFields() {
        ContractTemplate template = new ContractTemplate(
                "beginner_wheat",
                "DAILY",
                1,
                2,
                Map.of("wheat", 128L),
                5000,
                3,
                1,
                0,
                Map.of(),
                24
        );

        assertEquals("beginner_wheat", template.id());
        assertEquals("DAILY", template.type());
        assertEquals(1, template.tier());
        assertEquals(2, template.maxTier());
        assertEquals(Map.of("wheat", 128L), template.required());
        assertEquals(5000, template.money());
        assertEquals(3, template.research());
        assertEquals(1, template.reputation());
        assertEquals(0, template.debtRelief());
        assertEquals(Map.of(), template.itemRewards());
        assertEquals(24, template.expiresHours());
    }
}
