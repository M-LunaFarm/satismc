package kr.seungmin.satisskyfactory.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyService {
    boolean deposit(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    double balance(OfflinePlayer player);

    String name();
}
