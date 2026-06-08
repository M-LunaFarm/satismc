package kr.seungmin.satisskyfactory.market;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MarketService {
    private final StorageService storage;
    private final EconomyService economy;
    private final DatabaseService database;
    private final Map<String, Long> prices = new HashMap<>();
    private int personalSoftCap = 256;
    private double demandFloor = 0.35;

    public MarketService(StorageService storage, EconomyService economy, DatabaseService database) {
        this.storage = storage;
        this.economy = economy;
        this.database = database;
    }

    public void load(FileConfiguration config) {
        prices.clear();
        personalSoftCap = config.getInt("market.personal-soft-cap", 256);
        demandFloor = config.getDouble("market.demand-floor", 0.35);
        ConfigurationSection items = config.getConfigurationSection("market.items");
        if (items == null) {
            return;
        }
        for (String itemId : items.getKeys(false)) {
            prices.put(itemId, items.getLong(itemId + ".base-price", 1));
        }
    }

    public long price(String itemId, long amount) {
        long base = prices.getOrDefault(itemId, 0L);
        double factor = amount > personalSoftCap ? Math.max(demandFloor, (double) personalSoftCap / amount) : 1.0;
        return Math.max(0, Math.round(base * amount * factor));
    }

    public Optional<Long> sell(UUID islandUuid, OfflinePlayer owner, String itemId, long amount) {
        if (amount <= 0 || !prices.containsKey(itemId)) {
            return Optional.empty();
        }
        VirtualInventory inventory = storage.islandStorage(islandUuid);
        if (!inventory.remove(itemId, amount)) {
            return Optional.empty();
        }
        long money = price(itemId, amount);
        storage.save(inventory);
        economy.deposit(owner, money);
        database.addLedger(islandUuid, "MARKET_SELL", money, itemId + " x" + amount);
        return Optional.of(money);
    }

    public Optional<Long> sellDirect(UUID islandUuid, OfflinePlayer owner, String itemId, long amount) {
        if (amount <= 0 || !prices.containsKey(itemId)) {
            return Optional.empty();
        }
        long money = price(itemId, amount);
        economy.deposit(owner, money);
        database.addLedger(islandUuid, "MARKET_SELL_HAND", money, itemId + " x" + amount);
        return Optional.of(money);
    }

    public Map<String, Long> prices() {
        return Map.copyOf(prices);
    }
}
