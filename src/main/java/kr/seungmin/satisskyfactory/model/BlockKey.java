package kr.seungmin.satisskyfactory.model;

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

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public BlockKey relative(int dx, int dy, int dz) {
        return new BlockKey(world, x + dx, y + dy, z + dz);
    }
}
