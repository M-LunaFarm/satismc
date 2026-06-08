package kr.seungmin.satisskyfactory.recipe;

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
                    machineTypes(config, base),
                    readMap(section(config, base, "input", "inputs")),
                    readMap(section(config, base, "output", "outputs")),
                    readMap(config.getConfigurationSection(base + "byproducts")),
                    config.getDouble(base + "power-cost", config.getDouble(base + "power", 0.0)),
                    config.getInt(base + "min-tier", config.getInt(base + "minTier", 1)),
                    stringList(config, base + "research-required", base + "researchRequired"),
                    config.getDouble(base + "quality-chance", 0.0)
            ));
        }
    }

    public List<RecipeDefinition> recipesFor(String machineType) {
        return recipes.stream().filter(recipe -> recipe.supports(machineType)).toList();
    }

    private List<String> machineTypes(FileConfiguration config, String base) {
        List<String> machines = new ArrayList<>(config.getStringList(base + "machines"));
        String single = config.getString(base + "machine", "");
        if (machines.isEmpty() && single != null && !single.isBlank()) {
            machines.add(single);
        }
        return machines;
    }

    private ConfigurationSection section(FileConfiguration config, String base, String first, String second) {
        ConfigurationSection section = config.getConfigurationSection(base + first);
        return section == null ? config.getConfigurationSection(base + second) : section;
    }

    private List<String> stringList(FileConfiguration config, String firstPath, String secondPath) {
        List<String> values = new ArrayList<>(config.getStringList(firstPath));
        if (!values.isEmpty()) {
            return values;
        }
        values.addAll(config.getStringList(secondPath));
        String scalar = config.getString(firstPath, config.getString(secondPath, ""));
        if (values.isEmpty() && scalar != null && !scalar.isBlank()) {
            values.add(scalar);
        }
        return values;
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
