package kr.example.satisskyfactory.model;

import org.bukkit.Location;

import java.util.Objects;

public record BlockKey(String world, int x, int y, int z) {
    public static BlockKey from(Location location) {
        Objects.requireNonNull(location.getWorld(), "location world");
        return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public String databaseKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
