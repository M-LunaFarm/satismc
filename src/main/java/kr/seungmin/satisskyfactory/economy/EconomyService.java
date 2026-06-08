package kr.seungmin.satisskyfactory.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyService {
    boolean deposit(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    double balance(OfflinePlayer player);

    default double withdrawMaintenance(OfflinePlayer owner, Object island, double amount) {
        return withdraw(owner, amount) ? amount : 0.0;
    }

    String name();
}
