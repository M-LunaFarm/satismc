package kr.seungmin.satisskyfactory.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FactoryGuiHolder implements InventoryHolder {
    public record GuiAction(String type, String value) {
    }

    private final String type;
    private final UUID islandUuid;
    private final UUID machineId;
    private final int page;
    private Inventory inventory;
    private final Map<Integer, GuiAction> actions = new HashMap<>();

    public FactoryGuiHolder(String type, UUID islandUuid, UUID machineId) {
        this(type, islandUuid, machineId, 0);
    }

    public FactoryGuiHolder(String type, UUID islandUuid, UUID machineId, int page) {
        this.type = type;
        this.islandUuid = islandUuid;
        this.machineId = machineId;
        this.page = Math.max(0, page);
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

    public int page() {
        return page;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void action(int slot, String type, String value) {
        actions.put(slot, new GuiAction(type, value));
    }

    public Optional<GuiAction> action(int slot) {
        return Optional.ofNullable(actions.get(slot));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
