package kr.seungmin.satisskyfactory.economy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class EconomyModeFactory {
    private EconomyModeFactory() {
    }

    public static EconomyService create(JavaPlugin plugin, FileConfiguration config) {
        String mode = config.getString("economy.mode", "VAULT_PLAYER").toUpperCase(Locale.ROOT);
        EconomyService playerEconomy = playerEconomy(plugin, config.getBoolean("economy.use-vault", true));
        return select(mode, playerEconomy);
    }

    static EconomyService select(String mode, EconomyService playerEconomy) {
        return switch (mode) {
            case "ISLAND_BANK" -> new IslandBankEconomyService(playerEconomy);
            case "HYBRID" -> new HybridEconomyService(new IslandBankEconomyService(playerEconomy), playerEconomy);
            default -> playerEconomy;
        };
    }

    private static EconomyService playerEconomy(JavaPlugin plugin, boolean useVault) {
        if (!useVault) {
            return resolvePlayerEconomy(false, null);
        }
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return new FallbackEconomyService();
        }
        return resolvePlayerEconomy(true, VaultEconomyService.createOrFallback(plugin));
    }

    static EconomyService resolvePlayerEconomy(boolean useVault, EconomyService vaultEconomy) {
        if (!useVault || vaultEconomy == null) {
            return new FallbackEconomyService();
        }
        return vaultEconomy;
    }
}
