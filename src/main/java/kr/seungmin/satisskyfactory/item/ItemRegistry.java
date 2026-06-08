package kr.seungmin.satisskyfactory.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemRegistry {
    public record FactoryItem(String id, Material material, String displayName, int customModelData,
                              boolean virtualOnly, long basePrice, List<String> tags) {
    }

    private final Map<String, FactoryItem> items = new HashMap<>();

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
            items.put(id, new FactoryItem(
                    id,
                    material,
                    section.getString(id + ".display", id),
                    section.getInt(id + ".custom-model-data", 0),
                    section.getBoolean(id + ".virtual-only", false),
                    section.getLong(id + ".base-price", 0),
                    section.getStringList(id + ".tags")
            ));
        }
    }

    public Optional<FactoryItem> get(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public Map<String, FactoryItem> all() {
        return Collections.unmodifiableMap(items);
    }
}
