package kr.seungmin.satisskyfactory.hook;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class SuperiorSkyblockHook {
    public record IslandRef(Object raw, UUID islandUuid, UUID ownerUuid) {
    }

    private final JavaPlugin plugin;
    private boolean available;
    private Class<?> apiClass;

    public SuperiorSkyblockHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enable() {
        PluginManager plugins = plugin.getServer().getPluginManager();
        if (plugins.getPlugin("SuperiorSkyblock2") == null) {
            plugin.getLogger().severe("SuperiorSkyblock2 is required. Disabling SatisSkyFactory.");
            return false;
        }
        try {
            apiClass = Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
            available = true;
            return true;
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().log(Level.SEVERE, "SuperiorSkyblockAPI class was not found.", exception);
            return false;
        }
    }

    public Optional<IslandRef> getIslandAt(Location location) {
        if (!available) {
            return Optional.empty();
        }
        return islandRef(invokeStatic("getIslandAt", new Class<?>[]{Location.class}, location));
    }

    public Optional<IslandRef> getIslandOf(Player player) {
        if (!available) {
            return Optional.empty();
        }
        Object superiorPlayer = superiorPlayer(player);
        return superiorPlayer == null ? Optional.empty() : islandRef(invoke(superiorPlayer, "getIsland"));
    }

    public Optional<IslandRef> getIslandByUuid(UUID islandUuid) {
        if (!available) {
            return Optional.empty();
        }
        return islandRef(invokeStatic("getIslandByUUID", new Class<?>[]{UUID.class}, islandUuid));
    }

    public boolean canBuildFactory(Player player, Location location) {
        Optional<IslandRef> locationIsland = getIslandAt(location);
        return locationIsland.isPresent()
                && isLocationInsidePlayerIsland(player, location)
                && isPlayerIslandMember(player, locationIsland.get());
    }

    public boolean isLocationInsidePlayerIsland(Player player, Location location) {
        Optional<IslandRef> playerIsland = getIslandOf(player);
        Optional<IslandRef> locationIsland = getIslandAt(location);
        if (playerIsland.isEmpty() || locationIsland.isEmpty()) {
            return false;
        }
        if (!playerIsland.get().islandUuid().equals(locationIsland.get().islandUuid())) {
            return false;
        }
        Boolean inside = invokeBoolean(locationIsland.get().raw(), "isInside", location);
        return inside == null || inside;
    }

    public boolean isPlayerIslandMember(Player player, IslandRef island) {
        if (player.hasPermission("satisskyfactory.admin")) {
            return true;
        }
        Object superiorPlayer = superiorPlayer(player);
        Boolean member = superiorPlayer == null ? null : invokeBoolean(island.raw(), "isMember", superiorPlayer);
        return member != null ? member : player.getUniqueId().equals(island.ownerUuid());
    }

    private Optional<IslandRef> islandRef(Object island) {
        if (island == null) {
            return Optional.empty();
        }
        UUID islandUuid = extractUuid(island, "getUniqueId")
                .or(() -> extractUuid(island, "getUUID"))
                .orElseGet(() -> UUID.nameUUIDFromBytes(String.valueOf(island).getBytes()));
        UUID ownerUuid = extractOwnerUuid(island).orElse(islandUuid);
        return Optional.of(new IslandRef(island, islandUuid, ownerUuid));
    }

    private Optional<UUID> extractOwnerUuid(Object island) {
        Object owner = invoke(island, "getOwner");
        if (owner instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        if (owner == null) {
            return Optional.empty();
        }
        return extractUuid(owner, "getUniqueId")
                .or(() -> extractUuid(owner, "getUUID"))
                .or(() -> extractUuid(owner, "getUniqueID"));
    }

    private Optional<UUID> extractUuid(Object target, String methodName) {
        Object value = invoke(target, methodName);
        if (value instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        if (value != null) {
            try {
                return Optional.of(UUID.fromString(String.valueOf(value)));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Object superiorPlayer(Player player) {
        Object byPlayer = invokeStatic("getPlayer", new Class<?>[]{Player.class}, player);
        return byPlayer != null ? byPlayer : invokeStatic("getPlayer", new Class<?>[]{UUID.class}, player.getUniqueId());
    }

    private Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = apiClass.getMethod(methodName, parameterTypes);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }
        return null;
    }

    private Boolean invokeBoolean(Object target, String methodName, Object... args) {
        Object value = invoke(target, methodName, args);
        return value instanceof Boolean bool ? bool : null;
    }
}
