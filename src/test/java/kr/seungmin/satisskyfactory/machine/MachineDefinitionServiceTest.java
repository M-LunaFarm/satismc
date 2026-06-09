package kr.seungmin.satisskyfactory.machine;

import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineDefinitionServiceTest {
    @Test
    void defaultMachineConfigsUseGoalRoleAndIndustryEnums() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources", "machines.yml"));

        assertMachine(config, "harvester_t1", MachineIndustry.AGRICULTURE, MachineRole.PRODUCER);
        assertMachine(config, "bio_generator_t1", MachineIndustry.POWER, MachineRole.GENERATOR);
        assertMachine(config, "conveyor_t1", MachineIndustry.LOGISTICS, MachineRole.LOGISTICS);
    }

    private void assertMachine(YamlConfiguration config, String typeId, MachineIndustry industry, MachineRole role) {
        String base = "machines." + typeId + ".";
        assertTrue(config.isConfigurationSection("machines." + typeId));
        assertEquals(industry, MachineIndustry.fromConfig(config.getString(base + "industry")));
        assertEquals(role, MachineRole.fromConfig(config.getString(base + "role")));
    }

    @Test
    void unknownEnumValuesStayLoadable() {
        assertEquals(MachineIndustry.UNKNOWN, MachineIndustry.fromConfig("not_an_industry"));
        assertEquals(MachineRole.UNKNOWN, MachineRole.fromConfig("not_a_role"));
    }
}
