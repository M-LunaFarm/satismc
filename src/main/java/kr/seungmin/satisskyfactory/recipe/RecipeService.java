package kr.seungmin.satisskyfactory.recipe;

import kr.seungmin.satisskyfactory.config.RecipeConfigLoader;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class RecipeService {
    private final List<RecipeDefinition> recipes = new ArrayList<>();
    private final RecipeConfigLoader loader = new RecipeConfigLoader();

    public void load(FileConfiguration config) {
        recipes.clear();
        recipes.addAll(loader.load(config));
    }

    public List<RecipeDefinition> recipesFor(String machineType) {
        return recipes.stream().filter(recipe -> recipe.supports(machineType)).toList();
    }
}
