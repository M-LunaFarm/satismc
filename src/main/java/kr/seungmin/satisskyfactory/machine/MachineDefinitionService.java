package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.config.MachineConfigLoader;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MachineDefinitionService {
    private final Map<String, MachineDefinition> definitions = new HashMap<>();
    private final MachineConfigLoader loader = new MachineConfigLoader();

    public void load(FileConfiguration config) {
        definitions.clear();
        definitions.putAll(loader.load(config));
    }

    public Optional<MachineDefinition> get(String typeId) {
        return Optional.ofNullable(definitions.get(typeId));
    }

    public Collection<MachineDefinition> all() {
        return definitions.values();
    }
}
