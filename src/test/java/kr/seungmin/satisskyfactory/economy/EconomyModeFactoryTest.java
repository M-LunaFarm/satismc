package kr.seungmin.satisskyfactory.economy;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyModeFactoryTest {
    @Test
    void vaultPlayerModeUsesResolvedPlayerEconomy() {
        EconomyService playerEconomy = new NamedEconomy("PlayerEconomy");

        EconomyService selected = EconomyModeFactory.select("VAULT_PLAYER", playerEconomy);

        assertEquals("PlayerEconomy", selected.name());
    }

    @Test
    void islandBankAndHybridWrapResolvedPlayerEconomy() {
        EconomyService playerEconomy = new NamedEconomy("PlayerEconomy");

        EconomyService islandBank = EconomyModeFactory.select("ISLAND_BANK", playerEconomy);
        EconomyService hybrid = EconomyModeFactory.select("HYBRID", playerEconomy);

        assertEquals("IslandBank", islandBank.name());
        assertEquals("Hybrid", hybrid.name());
    }

    @Test
    void useVaultFalseSelectsFallbackPlayerEconomy() {
        EconomyService playerEconomy = EconomyModeFactory.resolvePlayerEconomy(false, new NamedEconomy("Vault"));

        assertEquals("Fallback", playerEconomy.name());
    }

    @Test
    void useVaultTrueKeepsVaultPlayerEconomy() {
        EconomyService playerEconomy = EconomyModeFactory.resolvePlayerEconomy(true, new NamedEconomy("Vault"));

        assertEquals("Vault", playerEconomy.name());
    }

    private record NamedEconomy(String name) implements EconomyService {
        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            return true;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return true;
        }

        @Override
        public double balance(OfflinePlayer player) {
            return 0;
        }
    }
}
