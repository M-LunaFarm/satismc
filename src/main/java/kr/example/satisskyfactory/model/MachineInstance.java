package kr.example.satisskyfactory.model;

import org.bukkit.block.BlockFace;

import java.util.UUID;

public final class MachineInstance {
    private final UUID machineId;
    private final UUID islandUuid;
    private final UUID ownerUuid;
    private final String typeId;
    private final int tier;
    private final BlockKey location;
    private BlockFace direction;
    private MachineStatus status;
    private UUID inputInventoryId;
    private UUID outputInventoryId;
    private UUID linkedResourceNodeId;
    private long lastProcessAt;
    private double wear;

    public MachineInstance(UUID machineId, UUID islandUuid, UUID ownerUuid, String typeId, int tier, BlockKey location) {
        this.machineId = machineId;
        this.islandUuid = islandUuid;
        this.ownerUuid = ownerUuid;
        this.typeId = typeId;
        this.tier = tier;
        this.location = location;
        this.direction = BlockFace.NORTH;
        this.status = MachineStatus.IDLE;
    }

    public UUID machineId() { return machineId; }
    public UUID islandUuid() { return islandUuid; }
    public UUID ownerUuid() { return ownerUuid; }
    public String typeId() { return typeId; }
    public int tier() { return tier; }
    public BlockKey location() { return location; }
    public BlockFace direction() { return direction; }
    public void direction(BlockFace direction) { this.direction = direction; }
    public MachineStatus status() { return status; }
    public void status(MachineStatus status) { this.status = status; }
    public UUID inputInventoryId() { return inputInventoryId; }
    public void inputInventoryId(UUID inputInventoryId) { this.inputInventoryId = inputInventoryId; }
    public UUID outputInventoryId() { return outputInventoryId; }
    public void outputInventoryId(UUID outputInventoryId) { this.outputInventoryId = outputInventoryId; }
    public UUID linkedResourceNodeId() { return linkedResourceNodeId; }
    public void linkedResourceNodeId(UUID linkedResourceNodeId) { this.linkedResourceNodeId = linkedResourceNodeId; }
    public long lastProcessAt() { return lastProcessAt; }
    public void lastProcessAt(long lastProcessAt) { this.lastProcessAt = lastProcessAt; }
    public double wear() { return wear; }
    public void wear(double wear) { this.wear = wear; }
}
