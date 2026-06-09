package kr.seungmin.satisskyfactory.machine;

import java.util.Locale;

public enum MachineRole {
    PRODUCER,
    PROCESSOR,
    GENERATOR,
    STORAGE,
    LOGISTICS,
    BATTERY,
    CONTROLLER,
    UNKNOWN;

    public static MachineRole fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return MachineRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
