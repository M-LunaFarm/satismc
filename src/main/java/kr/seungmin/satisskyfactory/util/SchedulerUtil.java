package kr.seungmin.satisskyfactory.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SchedulerUtil {
    private SchedulerUtil() {
    }

    public static long positiveTicks(long ticks) {
        return Math.max(1L, ticks);
    }

    public static BukkitTask repeating(JavaPlugin plugin, Runnable runnable, long intervalTicks) {
        long period = positiveTicks(intervalTicks);
        return Bukkit.getScheduler().runTaskTimer(plugin, runnable, period, period);
    }

    public static BukkitTask asyncRepeating(JavaPlugin plugin, Runnable runnable, long intervalTicks) {
        long period = positiveTicks(intervalTicks);
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, period, period);
    }
}
