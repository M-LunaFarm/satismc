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
    public record FactoryItem(String id, Material material, String displayName, int customModelData,
                              boolean virtualOnly, long basePrice, List<String> tags) {
    }

    private final Map<String, FactoryItem> items = new LinkedHashMap<>();

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
                    section.getString(id + ".display", section.getString(id + ".display-name", id)),
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

    public Optional<String> itemIdForMaterial(Material material) {
        return items.values().stream()
                .filter(item -> !item.virtualOnly())
                .filter(item -> item.material() == material)
                .map(FactoryItem::id)
                .findFirst();
    }

    public Map<String, FactoryItem> all() {
        return Collections.unmodifiableMap(items);
    }
}
