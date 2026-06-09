package kr.seungmin.satisskyfactory.contract;

import java.util.Map;

public record ContractTemplate(
        String id,
        String type,
        int tier,
        int maxTier,
        Map<String, Long> required,
        long money,
        long research,
        long reputation,
        long debtRelief,
        Map<String, Long> itemRewards,
        long expiresHours
) {
}
