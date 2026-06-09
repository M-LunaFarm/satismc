package kr.seungmin.satisskyfactory.model;

import java.util.UUID;

public final class ResourceNode {
    private final UUID nodeId;
    private final UUID islandUuid;
    private final String nodeType;
    private final String resourceId;
    private final double purity;
    private long remaining;
    private final long maxRemaining;
    private final long regenPerHour;
    private final int requiredMachineTier;
    private final BlockKey location;
    private long createdAt;
    private long updatedAt;

    public ResourceNode(UUID nodeId, UUID islandUuid, String nodeType, String resourceId, double purity, long remaining,
                        long maxRemaining, long regenPerHour, int requiredMachineTier, BlockKey location, long createdAt, long updatedAt) {
        this.nodeId = nodeId;
        this.islandUuid = islandUuid;
        this.nodeType = nodeType;
        this.resourceId = resourceId;
        this.purity = purity;
        this.remaining = remaining;
        this.maxRemaining = maxRemaining;
        this.regenPerHour = regenPerHour;
        this.requiredMachineTier = requiredMachineTier;
        this.location = location;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID nodeId() { return nodeId; }
    public UUID islandUuid() { return islandUuid; }
    public String nodeType() { return nodeType; }
    public String resourceId() { return resourceId; }
    public double purity() { return purity; }
    public long remaining() { return remaining; }
    public void remaining(long remaining) { this.remaining = Math.max(0, Math.min(maxRemaining, remaining)); }
    public long maxRemaining() { return maxRemaining; }
    public long regenPerHour() { return regenPerHour; }
    public int requiredMachineTier() { return requiredMachineTier; }
    public BlockKey location() { return location; }
    public long createdAt() { return createdAt; }
    public void createdAt(long createdAt) { this.createdAt = createdAt; }
    public long updatedAt() { return updatedAt; }
    public void updatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
