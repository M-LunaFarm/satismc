package kr.seungmin.satisskyfactory.config;

import kr.seungmin.satisskyfactory.item.ItemDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemConfigLoader {
    public Map<String, ItemDefinition> load(FileConfiguration config) {
        Map<String, ItemDefinition> items = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return items;
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
        return items;
    }
}
