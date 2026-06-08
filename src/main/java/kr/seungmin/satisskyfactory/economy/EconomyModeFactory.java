package kr.seungmin.satisskyfactory.economy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class EconomyModeFactory {
    private EconomyModeFactory() {
    }

    public static EconomyService create(JavaPlugin plugin, FileConfiguration config) {
        EconomyService playerEconomy = VaultEconomyService.createOrFallback(plugin);
        String mode = config.getString("economy.mode", "VAULT_PLAYER").toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "ISLAND_BANK" -> new IslandBankEconomyService(playerEconomy);
            case "HYBRID" -> new HybridEconomyService(new IslandBankEconomyService(playerEconomy), playerEconomy);
            default -> playerEconomy;
        };
    }
}
