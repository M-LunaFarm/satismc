package kr.seungmin.satisskyfactory.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomItemKeys {
    public static final String ITEM_ID = "item_id";
    public static final String MACHINE_TYPE = "machine_type";
    public static final String MACHINE_TIER = "machine_tier";
    public static final String INTERNAL_UUID = "internal_uuid";
    public static final String LEGACY_FACTORY_ITEM = "factory_item";

    private CustomItemKeys() {
    }

    public static NamespacedKey itemId(JavaPlugin plugin) {
        return new NamespacedKey(plugin, ITEM_ID);
    }

    public static NamespacedKey machineType(JavaPlugin plugin) {
        return new NamespacedKey(plugin, MACHINE_TYPE);
    }

    public static NamespacedKey machineTier(JavaPlugin plugin) {
        return new NamespacedKey(plugin, MACHINE_TIER);
    }

    public static NamespacedKey internalUuid(JavaPlugin plugin) {
        return new NamespacedKey(plugin, INTERNAL_UUID);
    }

    public static NamespacedKey legacyFactoryItem(JavaPlugin plugin) {
        return new NamespacedKey(plugin, LEGACY_FACTORY_ITEM);
    }
}
