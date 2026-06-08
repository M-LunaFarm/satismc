package kr.seungmin.satisskyfactory.market;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MarketService {
    public record SellResult(long gross, long paidToPlayer, long debtRepaid, double serverDemandFactor, double personalFactor) {
    }

    private final StorageService storage;
    private final EconomyService economy;
    private final DatabaseService database;
    private final Map<String, Long> prices = new HashMap<>();
    private final Map<String, Long> targetDailyAmounts = new HashMap<>();
    private int personalSoftCap = 256;
    private double demandFloor = 0.35;
    private double demandCeiling = 1.25;
    private double demandExponent = 0.35;
    private double debtRepayRate = 0.35;

    public MarketService(StorageService storage, EconomyService economy, DatabaseService database) {
        this.storage = storage;
        this.economy = economy;
        this.database = database;
    }

    public void load(FileConfiguration config) {
        prices.clear();
        targetDailyAmounts.clear();
        personalSoftCap = config.getInt("market.personal-soft-cap", 256);
        demandFloor = config.getDouble("market.demand-floor", 0.35);
        demandCeiling = config.getDouble("market.demand-ceiling", 1.25);
        demandExponent = config.getDouble("market.demand-exponent", 0.35);
        debtRepayRate = config.getDouble("market.debt-repay-rate", 0.35);
        ConfigurationSection items = config.getConfigurationSection("market.items");
        if (items == null) {
            return;
        }
        for (String itemId : items.getKeys(false)) {
            prices.put(itemId, items.getLong(itemId + ".base-price", 1));
            targetDailyAmounts.put(itemId, items.getLong(itemId + ".target-daily-amount", Math.max(1, personalSoftCap * 4L)));
        }
    }

    public long price(String itemId, long amount) {
        return Math.max(0, Math.round(prices.getOrDefault(itemId, 0L) * amount));
    }

    public long price(UUID islandUuid, String itemId, long amount) {
        Factors factors = factors(islandUuid, itemId, amount);
        return Math.max(0, Math.round(prices.getOrDefault(itemId, 0L) * amount * factors.serverDemandFactor() * factors.personalFactor()));
    }

    public Optional<SellResult> sell(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        if (amount <= 0 || !prices.containsKey(itemId)) {
            return Optional.empty();
        }
        VirtualInventory inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.remove(itemId, amount)) {
            return Optional.empty();
        }
        SellResult result = payout(island, owner, itemId, amount);
        storage.save(inventory);
        database.addLedger(island.islandUuid(), "MARKET_SELL", result.gross(), itemId + " x" + amount);
        return Optional.of(result);
    }

    public Optional<SellResult> sellDirect(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        if (amount <= 0 || !prices.containsKey(itemId)) {
            return Optional.empty();
        }
        SellResult result = payout(island, owner, itemId, amount);
        database.addLedger(island.islandUuid(), "MARKET_SELL_HAND", result.gross(), itemId + " x" + amount);
        return Optional.of(result);
    }

    public Map<String, Long> prices() {
        return Map.copyOf(prices);
    }

    private SellResult payout(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        Factors factors = factors(island.islandUuid(), itemId, amount);
        long gross = Math.max(0, Math.round(prices.getOrDefault(itemId, 0L) * amount * factors.serverDemandFactor() * factors.personalFactor()));
        long debtRepaid = 0;
        if (island.maintenanceDebt() > 0) {
            debtRepaid = Math.min(island.maintenanceDebt(), Math.round(gross * debtRepayRate));
            island.maintenanceDebt(island.maintenanceDebt() - debtRepaid);
            database.saveIsland(island);
        }
        long paid = Math.max(0, gross - debtRepaid);
        if (paid > 0) {
            economy.deposit(owner, paid);
        }
        String dateKey = dateKey();
        database.recordMarketSale(island.islandUuid(), itemId, dateKey, amount, factors.serverDemandFactor());
        if (debtRepaid > 0) {
            database.addLedger(island.islandUuid(), "MARKET_DEBT_REPAY", debtRepaid, itemId + " x" + amount);
        }
        return new SellResult(gross, paid, debtRepaid, factors.serverDemandFactor(), factors.personalFactor());
    }

    private Factors factors(UUID islandUuid, String itemId, long amount) {
        String dateKey = dateKey();
        long serverSold = database.marketDailySold(itemId, dateKey);
        long personalSold = database.marketPersonalSold(islandUuid, itemId, dateKey);
        double target = Math.max(1.0, targetDailyAmounts.getOrDefault(itemId, Math.max(1, personalSoftCap * 4L)));
        double serverFactor = Math.pow(target / Math.max(1.0, serverSold + amount), demandExponent);
        serverFactor = clamp(serverFactor, demandFloor, demandCeiling);
        double personalFactor = personalSold + amount > personalSoftCap
                ? Math.max(demandFloor, (double) personalSoftCap / (personalSold + amount))
                : 1.0;
        return new Factors(serverFactor, personalFactor);
    }

    private String dateKey() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Factors(double serverDemandFactor, double personalFactor) {
    }
}
