package kr.seungmin.satisskyfactory.item;

import kr.seungmin.satisskyfactory.model.MachineDefinition;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CustomItemFactory {
    private final NamespacedKey machineTypeKey;
    private final NamespacedKey machineTierKey;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey legacyFactoryItemKey;
    private final NamespacedKey internalUuidKey;

    public CustomItemFactory(JavaPlugin plugin) {
        this.machineTypeKey = new NamespacedKey(plugin, "machine_type");
        this.machineTierKey = new NamespacedKey(plugin, "machine_tier");
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
        this.legacyFactoryItemKey = new NamespacedKey(plugin, "factory_item");
        this.internalUuidKey = new NamespacedKey(plugin, "internal_uuid");
    }

    public ItemStack machineItem(MachineDefinition definition, int amount) {
        ItemStack stack = new ItemStack(definition.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + definition.displayName());
        meta.setLore(List.of(ChatColor.GRAY + "SatisSkyFactory machine", ChatColor.DARK_GRAY + definition.typeId()));
        meta.getPersistentDataContainer().set(machineTypeKey, PersistentDataType.STRING, definition.typeId());
        meta.getPersistentDataContainer().set(machineTierKey, PersistentDataType.INTEGER, definition.tier());
        meta.getPersistentDataContainer().set(internalUuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack factoryItem(ItemRegistry.FactoryItem item, int amount) {
        ItemStack stack = new ItemStack(item.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + item.displayName());
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, item.id());
        meta.getPersistentDataContainer().set(legacyFactoryItemKey, PersistentDataType.STRING, item.id());
        meta.getPersistentDataContainer().set(internalUuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<String> machineType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        return Optional.ofNullable(pdc.get(machineTypeKey, PersistentDataType.STRING));
    }

    public Optional<String> factoryItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String itemId = pdc.get(itemIdKey, PersistentDataType.STRING);
        if (itemId != null) {
            return Optional.of(itemId);
        }
        return Optional.ofNullable(pdc.get(legacyFactoryItemKey, PersistentDataType.STRING));
    }
}
