package kr.example.satisskyfactory.machine;

import kr.example.satisskyfactory.model.MachineDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MachineDefinitionService {
    private final Map<String, MachineDefinition> definitions = new HashMap<>();

    public void load(FileConfiguration config) {
        definitions.clear();
        ConfigurationSection section = config.getConfigurationSection("machines");
        if (section == null) {
            return;
        }
        for (String typeId : section.getKeys(false)) {
            String path = "machines." + typeId + ".";
            Material material = Material.matchMaterial(config.getString(path + "material", "STONE"));
            if (material == null) {
                material = Material.STONE;
            }
            definitions.put(typeId, new MachineDefinition(
                    typeId,
                    config.getString(path + "display", typeId),
                    material,
                    config.getInt(path + "tier", 1),
                    config.getInt(path + "input-capacity", 128),
                    config.getInt(path + "output-capacity", 512),
                    config.getDouble(path + "power-consumption", 0.0),
                    config.getDouble(path + "power-generation", 0.0),
                    config.getDouble(path + "battery-capacity", 0.0),
                    config.getInt(path + "cycle-ticks", 80),
                    config.getInt(path + "range", 0),
                    config.getInt(path + "amount-per-cycle", 1)
            ));
        }
    }

    public Optional<MachineDefinition> get(String typeId) {
        return Optional.ofNullable(definitions.get(typeId));
    }

    public Collection<MachineDefinition> all() {
        return definitions.values();
    }
}
