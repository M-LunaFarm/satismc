package kr.seungmin.satisskyfactory.machine;

import java.util.Locale;

public enum MachineIndustry {
    AGRICULTURE,
    MINING,
    FORESTRY,
    RANCHING,
    FISHING,
    MANUFACTURING,
    POWER,
    LOGISTICS,
    UNKNOWN;

    public static MachineIndustry fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return MachineIndustry.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
