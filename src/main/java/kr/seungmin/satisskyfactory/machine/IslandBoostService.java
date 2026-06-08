package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public final class IslandBoostService {
    public record Settings(double agricultureMin, double agricultureMax, int factorySlotSizeDivisor,
                           double contractSlotWorthLogBase, int maxContractSlotBonus) {
        public static Settings defaults() {
            return new Settings(1.0, 3.0, 50, 10.0, 0);
        }
    }

    public record Boosts(double agricultureBoost, int factorySlotBonus, int contractSlotBonus) {
        public int machineLimit(int baseLimit) {
            return baseLimit + factorySlotBonus;
        }
    }

    private final SuperiorSkyblockHook skyblock;
    private Settings settings = Settings.defaults();
    private Boosts fallbackBoosts = new Boosts(1.0, 0, 0);

    public IslandBoostService(SuperiorSkyblockHook skyblock) {
        this.skyblock = skyblock;
    }

    public IslandBoostService(SuperiorSkyblockHook skyblock, Settings settings, Boosts fallbackBoosts) {
        this.skyblock = skyblock;
        this.settings = settings == null ? Settings.defaults() : settings;
        this.fallbackBoosts = fallbackBoosts == null ? new Boosts(1.0, 0, 0) : fallbackBoosts;
    }

    public void configure(FileConfiguration config) {
        settings = new Settings(
                Math.max(0.0, config.getDouble("superior-skyblock.boosts.agriculture-min", 1.0)),
                Math.max(0.0, config.getDouble("superior-skyblock.boosts.agriculture-max", 3.0)),
                Math.max(1, config.getInt("superior-skyblock.boosts.factory-slot-size-divisor", 50)),
                Math.max(1.01, config.getDouble("superior-skyblock.boosts.contract-slot-worth-log-base", 10.0)),
                Math.max(0, config.getInt("superior-skyblock.boosts.max-contract-slot-bonus", 0))
        );
    }

    public Boosts boosts(UUID islandUuid) {
        if (skyblock == null) {
            return fallbackBoosts;
        }
        return skyblock.getIslandByUuid(islandUuid).map(ref -> boosts(ref.raw())).orElse(fallbackBoosts);
    }

    public Boosts boosts(Object rawIsland) {
        double min = Math.min(settings.agricultureMin(), settings.agricultureMax());
        double max = Math.max(settings.agricultureMin(), settings.agricultureMax());
        double crop = clamp(numberAny(rawIsland, "getCropGrowthMultiplier", "getCropGrowth", "getCropGrowthRate")
                .orElse(1.0), min, max);
        int size = numberAny(rawIsland, "getIslandSize", "getSize", "getRadius").map(Double::intValue).orElse(0);
        double worth = numberAny(rawIsland, "getWorth", "getIslandWorth", "getLevel").orElse(1.0);
        int factorySlotBonus = Math.max(0, size / settings.factorySlotSizeDivisor());
        int contractSlotBonus = Math.max(0, (int) Math.floor(
                Math.log(Math.max(1.0, worth)) / Math.log(settings.contractSlotWorthLogBase()) + 1.0e-9));
        if (settings.maxContractSlotBonus() > 0) {
            contractSlotBonus = Math.min(settings.maxContractSlotBonus(), contractSlotBonus);
        }
        return new Boosts(crop, factorySlotBonus, contractSlotBonus);
    }

    private Optional<Double> numberAny(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Optional<Double> value = number(target, methodName);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<Double> number(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Number number) {
                return Optional.of(number.doubleValue());
            }
            if (value instanceof BigDecimal decimal) {
                return Optional.of(decimal.doubleValue());
            }
            if (value != null) {
                return Optional.of(Double.parseDouble(String.valueOf(value)));
            }
        } catch (ReflectiveOperationException | NumberFormatException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
