package kr.example.satisskyfactory.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RecipeService {
    private final List<RecipeDefinition> recipes = new ArrayList<>();

    public void load(FileConfiguration config) {
        recipes.clear();
        ConfigurationSection section = config.getConfigurationSection("recipes");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String base = "recipes." + id + ".";
            recipes.add(new RecipeDefinition(
                    id,
                    config.getString(base + "machine", ""),
                    readMap(config.getConfigurationSection(base + "input")),
                    readMap(config.getConfigurationSection(base + "output")),
                    config.getDouble(base + "power", 0.0)
            ));
        }
    }

    public List<RecipeDefinition> recipesFor(String machineType) {
        return recipes.stream().filter(recipe -> recipe.machineType().equals(machineType)).toList();
    }

    private Map<String, Long> readMap(ConfigurationSection section) {
        Map<String, Long> result = new HashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            result.put(key, section.getLong(key));
        }
        return result;
    }
}
