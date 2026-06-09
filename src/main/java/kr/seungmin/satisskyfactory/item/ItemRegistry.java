package kr.seungmin.satisskyfactory.item;

import kr.seungmin.satisskyfactory.config.ItemConfigLoader;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ItemRegistry {
    private final Map<String, ItemDefinition> items = new LinkedHashMap<>();
    private final ItemConfigLoader loader = new ItemConfigLoader();

    public void load(FileConfiguration config) {
        items.clear();
        items.putAll(loader.load(config));
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

    public boolean isVirtualOnly(String itemId) {
        return get(itemId).map(ItemDefinition::virtualOnly).orElse(false);
    }

    public Map<String, ItemDefinition> all() {
        return Collections.unmodifiableMap(items);
    }
}
