package kr.seungmin.satisskyfactory.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class VaultHook {
    private final JavaPlugin plugin;

    public VaultHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVaultInstalled() {
        return plugin.getServer().getPluginManager().getPlugin("Vault") != null;
    }

    public Optional<Economy> economy() {
        if (!isVaultInstalled()) {
            return Optional.empty();
        }
        return providerEconomy(plugin.getServer().getServicesManager().getRegistration(Economy.class));
    }

    static Optional<Economy> providerEconomy(RegisteredServiceProvider<Economy> provider) {
        if (provider == null || provider.getProvider() == null) {
            return Optional.empty();
        }
        return Optional.of(provider.getProvider());
    }
}
