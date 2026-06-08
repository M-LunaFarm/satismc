package kr.seungmin.satisskyfactory.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public record FactoryGuiHolder(String type, UUID islandUuid, UUID machineId) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
