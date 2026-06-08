package kr.seungmin.satisskyfactory.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class FactoryGuiHolder implements InventoryHolder {
    private final String type;
    private final UUID islandUuid;
    private final UUID machineId;
    private Inventory inventory;

    public FactoryGuiHolder(String type, UUID islandUuid, UUID machineId) {
        this.type = type;
        this.islandUuid = islandUuid;
        this.machineId = machineId;
    }

    public String type() {
        return type;
    }

    public UUID islandUuid() {
        return islandUuid;
    }

    public UUID machineId() {
        return machineId;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
