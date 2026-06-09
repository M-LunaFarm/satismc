package kr.seungmin.satisskyfactory.economy;

import kr.seungmin.satisskyfactory.hook.VaultHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultEconomyService implements EconomyService {
    private final Economy economy;

    private VaultEconomyService(Economy economy) {
        this.economy = economy;
    }

    public static EconomyService createOrFallback(JavaPlugin plugin) {
        return createOrFallback(new VaultHook(plugin));
    }

    public static EconomyService createOrFallback(VaultHook vaultHook) {
        return vaultHook.economy()
                .<EconomyService>map(VaultEconomyService::new)
                .orElseGet(FallbackEconomyService::new);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public double balance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    @Override
    public String name() {
        return "Vault";
    }
}
