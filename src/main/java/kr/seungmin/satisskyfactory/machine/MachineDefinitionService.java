package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.ResourceNodeType;
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
                    config.getInt(path + "amount-per-cycle", 1),
                    config.getInt(path + "logistics-throughput", 0),
                    config.getDouble(path + "wear-per-cycle", 0.02),
                    config.getStringList(path + "required-unlocks"),
                    nodeType(config.getString(path + "node-type", "")),
                    harvestDrops(config.getConfigurationSection(path + "harvest-drops")),
                    plantRules(config.getConfigurationSection(path + "planting")),
                    config.getString(path + "fertilizer.item", "fertilizer"),
                    config.getInt(path + "fertilizer.growth-per-cycle", 1),
                    config.getDouble(path + "fertilizer.quality-chance", 0.0),
                    config.getString(path + "fertilizer.quality-item", "")
            ));
        }
    }

    public Optional<MachineDefinition> get(String typeId) {
        return Optional.ofNullable(definitions.get(typeId));
    }

    public Collection<MachineDefinition> all() {
        return definitions.values();
    }

    private ResourceNodeType nodeType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ResourceNodeType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Map<Material, String> harvestDrops(ConfigurationSection section) {
        Map<Material, String> drops = new HashMap<>();
        if (section == null) {
            return drops;
        }
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            String itemId = section.getString(materialName);
            if (material != null && itemId != null && !itemId.isBlank()) {
                drops.put(material, itemId);
            }
        }
        return drops;
    }

    private Map<String, MachineDefinition.PlantRule> plantRules(ConfigurationSection section) {
        Map<String, MachineDefinition.PlantRule> rules = new HashMap<>();
        if (section == null) {
            return rules;
        }
        for (String seedId : section.getKeys(false)) {
            Material crop = Material.matchMaterial(section.getString(seedId + ".crop", ""));
            Material soil = Material.matchMaterial(section.getString(seedId + ".soil", "FARMLAND"));
            if (crop != null && soil != null) {
                rules.put(seedId, new MachineDefinition.PlantRule(crop, soil));
            }
        }
        return rules;
    }
}
