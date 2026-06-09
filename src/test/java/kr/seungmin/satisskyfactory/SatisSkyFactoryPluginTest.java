package kr.seungmin.satisskyfactory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SatisSkyFactoryPluginTest {
    @Test
    void dirtySavePeriodUsesDatabaseSaveSecondsFirst() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.save-interval-seconds", 60);
        config.set("settings.dirty-save-period-ticks", 200);

        assertEquals(1200, SatisSkyFactoryPlugin.dirtySavePeriodTicks(config));
    }

    @Test
    void dirtySavePeriodFallsBackToLegacyTickSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.dirty-save-period-ticks", 200);

        assertEquals(200, SatisSkyFactoryPlugin.dirtySavePeriodTicks(config));
    }

    @Test
    void activeParticleLimitFollowsVisualsToggleAndCapsPerTick() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("visuals.particles", true);

        assertEquals(64, SatisSkyFactoryPlugin.activeParticleLimit(config, 300));

        config.set("visuals.particles", false);
        assertEquals(0, SatisSkyFactoryPlugin.activeParticleLimit(config, 300));
    }
}
