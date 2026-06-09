package kr.seungmin.satisskyfactory.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemRegistry {
    private final Map<String, ItemDefinition> items = new LinkedHashMap<>();

    public void load(FileConfiguration config) {
        items.clear();
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Material material = Material.matchMaterial(section.getString(id + ".material", "STONE"));
            if (material == null) {
                material = Material.STONE;
            }
            items.put(id, new ItemDefinition(
                    id,
                    material,
                    section.getString(id + ".display", section.getString(id + ".display-name", id)),
                    section.getInt(id + ".custom-model-data", 0),
                    section.getBoolean(id + ".virtual-only", false),
                    section.getLong(id + ".base-price", 0),
                    section.getStringList(id + ".tags")
            ));
        }
    }

    public Optional<ItemDefinition> get(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public Optional<String> itemIdForMaterial(Material material) {
        return items.values().stream()
                .filter(item -> !item.virtualOnly())
                .filter(item -> item.material() == material)
                .map(ItemDefinition::id)
                .findFirst();
    }

    public Map<String, ItemDefinition> all() {
        return Collections.unmodifiableMap(items);
    }
}
