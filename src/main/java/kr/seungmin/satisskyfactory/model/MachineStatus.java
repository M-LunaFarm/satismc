package kr.seungmin.satisskyfactory.model;

public enum MachineStatus {
    ACTIVE,
    SLEEPING,
    NO_POWER,
    NO_INPUT,
    OUTPUT_FULL,
    MAINTENANCE_LOCKED,
    BROKEN,
    CHUNK_UNLOADED,
    INVALID_LOCATION;

    public static MachineStatus fromStoredValue(String value) {
        return switch (value) {
            case "RUNNING" -> ACTIVE;
            case "IDLE" -> SLEEPING;
            case "INPUT_MISSING" -> NO_INPUT;
            case "LOCKED" -> MAINTENANCE_LOCKED;
            default -> MachineStatus.valueOf(value);
        };
    }
}
