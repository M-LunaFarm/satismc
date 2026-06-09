package kr.seungmin.satisskyfactory.research;

import java.util.List;

public record UnlockDefinition(
        String id,
        String displayName,
        long cost,
        long moneyCost,
        long requiredReputation,
        List<String> requires,
        List<String> grants,
        int factoryTier
) {
}
