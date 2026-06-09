package kr.seungmin.satisskyfactory.item;

import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
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
    private final MachineDefinitionService definitions;
    private final NamespacedKey machineTypeKey;
    private final NamespacedKey machineTierKey;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey legacyFactoryItemKey;
    private final NamespacedKey internalUuidKey;

    public CustomItemFactory(JavaPlugin plugin) {
        this(plugin, null);
    }

    public CustomItemFactory(JavaPlugin plugin, MachineDefinitionService definitions) {
        this.definitions = definitions;
        this.machineTypeKey = CustomItemKeys.machineType(plugin);
        this.machineTierKey = CustomItemKeys.machineTier(plugin);
        this.itemIdKey = CustomItemKeys.itemId(plugin);
        this.legacyFactoryItemKey = CustomItemKeys.legacyFactoryItem(plugin);
        this.internalUuidKey = CustomItemKeys.internalUuid(plugin);
    }

    public ItemStack createMachineItem(String typeId, int amount) {
        if (definitions == null) {
            throw new IllegalStateException("Machine definitions are not attached to this item factory");
        }
        MachineDefinition definition = definitions.get(typeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown machine type: " + typeId));
        return machineItem(definition, amount);
    }

    public ItemStack machineItem(MachineDefinition definition, int amount) {
        ItemStack stack = new ItemStack(definition.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + definition.displayName());
        meta.setLore(List.of(ChatColor.GRAY + "SatisSkyFactory machine", ChatColor.DARK_GRAY + definition.typeId()));
        if (definition.customModelData() > 0) {
            meta.setCustomModelData(definition.customModelData());
        }
        meta.getPersistentDataContainer().set(machineTypeKey, PersistentDataType.STRING, definition.typeId());
        meta.getPersistentDataContainer().set(machineTierKey, PersistentDataType.INTEGER, definition.tier());
        meta.getPersistentDataContainer().set(internalUuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack factoryItem(ItemDefinition item, int amount) {
        ItemStack stack = new ItemStack(item.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + item.displayName());
        if (item.customModelData() > 0) {
            meta.setCustomModelData(item.customModelData());
        }
        if (item.virtualOnly() || item.basePrice() > 0 || !item.tags().isEmpty()) {
            meta.setLore(itemLore(item));
        }
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

    public boolean isMachineItem(ItemStack stack) {
        return machineType(stack).isPresent();
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

    private List<String> itemLore(ItemDefinition item) {
        java.util.ArrayList<String> lore = new java.util.ArrayList<>();
        if (item.virtualOnly()) {
            lore.add(ChatColor.DARK_GRAY + "Virtual factory item");
        }
        if (item.basePrice() > 0) {
            lore.add(ChatColor.GRAY + "Base price: " + item.basePrice());
        }
        if (!item.tags().isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "Tags: " + String.join(", ", item.tags()));
        }
        return lore;
    }
}
