package kr.seungmin.satisskyfactory.machine;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IslandBoostServiceTest {
    @Test
    void usesConfigurableBoostFormula() {
        IslandBoostService boosts = new IslandBoostService(null);
        YamlConfiguration config = new YamlConfiguration();
        config.set("superior-skyblock.boosts.agriculture-min", 1.0);
        config.set("superior-skyblock.boosts.agriculture-max", 2.5);
        config.set("superior-skyblock.boosts.factory-slot-size-divisor", 40);
        config.set("superior-skyblock.boosts.contract-slot-worth-log-base", 10.0);
        config.set("superior-skyblock.boosts.max-contract-slot-bonus", 4);
        boosts.configure(config);

        IslandBoostService.Boosts result = boosts.boosts(new FakeIsland(3.8, 125, 100_000));

        assertEquals(2.5, result.agricultureBoost());
        assertEquals(3, result.factorySlotBonus());
        assertEquals(4, result.contractSlotBonus());
        assertEquals(131, result.machineLimit(128));
    }

    @Test
    void fallsBackToAlternateSuperiorSkyblockMethodNames() {
        IslandBoostService boosts = new IslandBoostService(null);

        IslandBoostService.Boosts result = boosts.boosts(new AlternateIsland(1.75, 90, 1000));

        assertEquals(1.75, result.agricultureBoost());
        assertEquals(1, result.factorySlotBonus());
        assertEquals(3, result.contractSlotBonus());
    }

    public record FakeIsland(double cropGrowthMultiplier, int islandSize, double worth) {
        public double getCropGrowthMultiplier() {
            return cropGrowthMultiplier;
        }

        public int getIslandSize() {
            return islandSize;
        }

        public double getWorth() {
            return worth;
        }
    }

    public record AlternateIsland(double cropGrowthRate, int size, double level) {
        public double getCropGrowthRate() {
            return cropGrowthRate;
        }

        public int getSize() {
            return size;
        }

        public double getLevel() {
            return level;
        }
    }
}
