package kr.seungmin.satisskyfactory.economy;

import org.bukkit.OfflinePlayer;

public final class HybridEconomyService implements EconomyService {
    private final IslandBankEconomyService islandBank;
    private final EconomyService playerEconomy;

    public HybridEconomyService(IslandBankEconomyService islandBank, EconomyService playerEconomy) {
        this.islandBank = islandBank;
        this.playerEconomy = playerEconomy;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return playerEconomy.deposit(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return playerEconomy.withdraw(player, amount);
    }

    @Override
    public double balance(OfflinePlayer player) {
        return playerEconomy.balance(player);
    }

    @Override
    public double withdrawMaintenance(OfflinePlayer owner, Object island, double amount) {
        double fromIsland = islandBank.withdrawMaintenance(owner, island, amount);
        double remaining = Math.max(0.0, amount - fromIsland);
        if (remaining <= 0) {
            return fromIsland;
        }
        return fromIsland + (playerEconomy.withdrawMaintenance(owner, island, remaining));
    }

    @Override
    public String name() {
        return "Hybrid";
    }
}
