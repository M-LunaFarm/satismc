package kr.seungmin.satisskyfactory.model;

public enum ResourceNodeType {
    ORE,
    MINERAL,
    FOREST,
    FISHING,
    PASTURE,
    WATER,
    OIL;

    public static ResourceNodeType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.equalsIgnoreCase("ORE") ? "MINERAL" : value;
        try {
            return ResourceNodeType.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public boolean matches(String value) {
        ResourceNodeType other = fromConfig(value);
        if (other == null) {
            return false;
        }
        return canonical() == other.canonical();
    }

    private ResourceNodeType canonical() {
        return this == ORE ? MINERAL : this;
    }
}
