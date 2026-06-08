package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public final class IslandBoostService {
    public record Boosts(double agricultureBoost, int factorySlotBonus, int contractSlotBonus) {
        public int machineLimit(int baseLimit) {
            return baseLimit + factorySlotBonus;
        }
    }

    private final SuperiorSkyblockHook skyblock;

    public IslandBoostService(SuperiorSkyblockHook skyblock) {
        this.skyblock = skyblock;
    }

    public Boosts boosts(UUID islandUuid) {
        return skyblock.getIslandByUuid(islandUuid).map(ref -> boosts(ref.raw())).orElse(new Boosts(1.0, 0, 0));
    }

    public Boosts boosts(Object rawIsland) {
        double crop = clamp(number(rawIsland, "getCropGrowthMultiplier").orElse(1.0), 1.0, 3.0);
        int size = number(rawIsland, "getIslandSize").map(Double::intValue).orElse(0);
        double worth = number(rawIsland, "getWorth").orElse(1.0);
        int factorySlotBonus = Math.max(0, size / 50);
        int contractSlotBonus = Math.max(0, (int) Math.floor(Math.log10(Math.max(1.0, worth))));
        return new Boosts(crop, factorySlotBonus, contractSlotBonus);
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
