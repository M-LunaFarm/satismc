package kr.seungmin.satisskyfactory.economy;

import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FallbackEconomyService implements EconomyService {
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        balances.merge(player.getUniqueId(), Math.max(0, amount), Double::sum);
        return true;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (balance(player) < amount) {
            return false;
        }
        balances.merge(player.getUniqueId(), -amount, Double::sum);
        return true;
    }

    @Override
    public double balance(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    @Override
    public String name() {
        return "Fallback";
    }
}
