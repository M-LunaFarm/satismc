package kr.seungmin.satisskyfactory.contract;

import java.util.Map;
import java.util.UUID;

public record Contract(
        UUID contractId,
        UUID islandUuid,
        String templateId,
        int tier,
        Type type,
        Map<String, Long> requiredItems,
        Rewards rewards,
        Map<String, Long> progress,
        Status status,
        long expiresAt
) {
    public Contract {
        requiredItems = Map.copyOf(requiredItems);
        progress = Map.copyOf(progress);
    }

    public Contract completed(Map<String, Long> completedProgress) {
        return new Contract(contractId, islandUuid, templateId, tier, type, requiredItems, rewards,
                completedProgress, Status.COMPLETED, expiresAt);
    }

    public enum Type {
        DAILY,
        WEEKLY,
        EMERGENCY,
        STORY,
        MARKET;

        public static Type fromStoredValue(String value) {
            return Type.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        }
    }

    public enum Status {
        ACTIVE,
        COMPLETED,
        EXPIRED,
        CANCELLED;

        public static Status fromStoredValue(String value) {
            return Status.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        }
    }

    public record Rewards(long money, long researchPoints, long reputation, long debtRelief, Map<String, Long> items) {
        public Rewards {
            items = Map.copyOf(items);
        }
    }
}
