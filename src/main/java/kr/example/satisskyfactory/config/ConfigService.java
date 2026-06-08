package kr.example.satisskyfactory.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigService {
    private static final List<String> FILES = List.of(
            "config.yml",
            "machines.yml",
            "items.yml",
            "recipes.yml",
            "resource-nodes.yml",
            "market.yml",
            "contracts.yml",
            "research.yml",
            "maintenance.yml",
            "messages.yml"
    );

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        configs.clear();
        for (String fileName : FILES) {
            plugin.saveResource(fileName, false);
            configs.put(fileName, YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), fileName)));
        }
    }

    public FileConfiguration main() {
        return file("config.yml");
    }

    public FileConfiguration file(String name) {
        FileConfiguration configuration = configs.get(name);
        if (configuration == null) {
            throw new IllegalArgumentException("Unknown config file: " + name);
        }
        return configuration;
    }
}
