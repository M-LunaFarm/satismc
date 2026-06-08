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

    public ResourceNode(UUID nodeId, UUID islandUuid, String nodeType, String resourceId, double purity, long remaining,
                        long maxRemaining, long regenPerHour, int requiredMachineTier, BlockKey location) {
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
}
