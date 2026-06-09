package kr.seungmin.satisskyfactory.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultHookTest {
    @Test
    void missingProviderReturnsEmptyEconomy() {
        assertTrue(VaultHook.providerEconomy(null).isEmpty());
    }

    @Test
    void providerReturnsVaultEconomy() {
        Economy economy = proxy(Economy.class);
        Plugin plugin = proxy(Plugin.class);
        RegisteredServiceProvider<Economy> provider = new RegisteredServiceProvider<>(
                Economy.class,
                economy,
                ServicePriority.Normal,
                plugin
        );

        assertSame(economy, VaultHook.providerEconomy(provider).orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getReturnType() == boolean.class) {
                return false;
            }
            if (method.getReturnType() == int.class) {
                return 0;
            }
            if (method.getReturnType() == double.class) {
                return 0.0;
            }
            return null;
        });
    }
}
