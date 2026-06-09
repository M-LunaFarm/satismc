package kr.seungmin.satisskyfactory.util;

import kr.seungmin.satisskyfactory.model.BlockKey;
import org.bukkit.Location;

import java.util.Objects;

public record LocationKey(String world, int x, int y, int z) {
    public static LocationKey from(Location location) {
        Objects.requireNonNull(location.getWorld(), "location world");
        return new LocationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static LocationKey from(BlockKey blockKey) {
        return new LocationKey(blockKey.world(), blockKey.x(), blockKey.y(), blockKey.z());
    }

    public BlockKey toBlockKey() {
        return new BlockKey(world, x, y, z);
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

    public LocationKey relative(int dx, int dy, int dz) {
        return new LocationKey(world, x + dx, y + dy, z + dz);
    }
}
