package kr.seungmin.satisskyfactory.market;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.item.ItemDefinition;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MarketService {
    public record SellResult(long gross, long paidToPlayer, long debtRepaid, double serverDemandFactor,
                             double personalFactor, double qualityFactor) {
    }

    private final StorageService storage;
    private final EconomyService economy;
    private final DatabaseService database;
    private final ItemRegistry items;
    private final Map<String, Long> prices = new HashMap<>();
    private final Map<String, Long> targetDailyAmounts = new HashMap<>();
    private final Map<String, Double> itemQualityFactors = new HashMap<>();
    private final Map<String, Double> tagQualityFactors = new HashMap<>();
    private final List<PriceCalculator.PersonalTier> personalTiers = new ArrayList<>();
    private boolean personalSoftCapEnabled = true;
    private int personalSoftCap = 256;
    private double demandFloor = 0.35;
    private double demandCeiling = 1.25;
    private double demandExponent = 0.35;
    private double debtRepayRate = 0.35;
    private double lockedDebtRepayRate = 0.70;
    private boolean lockedMarketSalesBlocked;

    public MarketService(StorageService storage, EconomyService economy, DatabaseService database, ItemRegistry items) {
        this.storage = storage;
        this.economy = economy;
        this.database = database;
        this.items = items;
    }

    public void load(FileConfiguration config) {
        load(config, null);
    }

    public void load(FileConfiguration config, FileConfiguration maintenanceConfig) {
        prices.clear();
        targetDailyAmounts.clear();
        itemQualityFactors.clear();
        tagQualityFactors.clear();
        personalTiers.clear();
        lockedMarketSalesBlocked = false;
        personalSoftCapEnabled = config.getBoolean("market.personal-soft-cap.enabled", true);
        personalSoftCap = config.isInt("market.personal-soft-cap") ? config.getInt("market.personal-soft-cap", 256) : 256;
        demandFloor = config.getDouble("market.factor-min", config.getDouble("market.demand-floor", 0.35));
        demandCeiling = config.getDouble("market.factor-max", config.getDouble("market.demand-ceiling", 1.25));
        demandExponent = config.getDouble("market.demand-exponent", 0.35);
        debtRepayRate = config.getDouble("market.debt-repay-rate", 0.35);
        lockedDebtRepayRate = config.getDouble("market.locked-debt-repay-rate", 0.70);
        if (maintenanceConfig != null) {
            lockedMarketSalesBlocked = maintenanceConfig.getBoolean("maintenance.locked.block-market-sales", false);
            if (maintenanceConfig.contains("maintenance.locked.auto-pay-debt-from-sales-percent")) {
                lockedDebtRepayRate = maintenanceConfig.getDouble("maintenance.locked.auto-pay-debt-from-sales-percent", 70.0) / 100.0;
            }
        }
        loadPersonalTiers(config);
        loadQualityFactors(config);
        ConfigurationSection marketItems = config.getConfigurationSection("market.items");
        if (marketItems == null) {
            return;
        }
        for (String itemId : marketItems.getKeys(false)) {
            prices.put(itemId, marketItems.contains(itemId + ".base-price")
                    ? marketItems.getLong(itemId + ".base-price", 1)
                    : itemBasePrice(itemId));
            targetDailyAmounts.put(itemId, marketItems.getLong(itemId + ".target-daily-amount", Math.max(1, personalSoftCap * 4L)));
            if (marketItems.contains(itemId + ".quality-factor")) {
                itemQualityFactors.put(itemId, Math.max(0.0, marketItems.getDouble(itemId + ".quality-factor", 1.0)));
            }
        }
    }

    public long price(String itemId, long amount) {
        return calculator().basePrice(itemId, amount);
    }

    public long price(UUID islandUuid, String itemId, long amount) {
        String dateKey = dateKey();
        return calculator().finalPrice(
                itemId,
                amount,
                database.marketDailySold(itemId, dateKey) + amount,
                database.marketPersonalSold(islandUuid, itemId, dateKey) + amount
        );
    }

    public Optional<SellResult> sell(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        if (amount <= 0 || !prices.containsKey(itemId) || marketBlocked(island)) {
            return Optional.empty();
        }
        VirtualInventory inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.remove(itemId, amount)) {
            return Optional.empty();
        }
        Optional<SellResult> result = payout(island, owner, itemId, amount);
        if (result.isEmpty()) {
            inventory.add(itemId, amount);
            storage.save(inventory);
            return Optional.empty();
        }
        storage.save(inventory);
        database.addLedger(island.islandUuid(), "MARKET_SELL", result.get().gross(), itemId + " x" + amount);
        return result;
    }

    public Optional<SellResult> sellDirect(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        if (amount <= 0 || !prices.containsKey(itemId) || marketBlocked(island)) {
            return Optional.empty();
        }
        Optional<SellResult> result = payout(island, owner, itemId, amount);
        result.ifPresent(sale -> database.addLedger(island.islandUuid(), "MARKET_SELL_HAND", sale.gross(), itemId + " x" + amount));
        return result;
    }

    public Map<String, Long> prices() {
        return Map.copyOf(prices);
    }

    private long itemBasePrice(String itemId) {
        return items.get(itemId)
                .map(ItemDefinition::basePrice)
                .filter(price -> price > 0)
                .orElse(1L);
    }

    private boolean marketBlocked(FactoryIsland island) {
        return lockedMarketSalesBlocked && island.maintenanceStatus() == MaintenanceStatus.LOCKED;
    }

    private Optional<SellResult> payout(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        String dateKey = dateKey();
        long serverSold = database.marketDailySold(itemId, dateKey) + amount;
        long personalSold = database.marketPersonalSold(island.islandUuid(), itemId, dateKey) + amount;
        PriceCalculator.Factors factors = calculator().factors(itemId, amount, serverSold, personalSold);
        long gross = calculator().finalPrice(itemId, amount, serverSold, personalSold);
        long debtRepaid = 0;
        if (island.maintenanceDebt() > 0) {
            double repayRate = island.maintenanceStatus() == MaintenanceStatus.LOCKED ? lockedDebtRepayRate : debtRepayRate;
            debtRepaid = Math.min(island.maintenanceDebt(), Math.round(gross * clamp(repayRate, 0.0, 1.0)));
        }
        long paid = Math.max(0, gross - debtRepaid);
        if (paid > 0 && !economy.deposit(owner, paid)) {
            return Optional.empty();
        }
        if (debtRepaid > 0) {
            island.maintenanceDebt(island.maintenanceDebt() - debtRepaid);
            database.saveIsland(island);
        }
        database.recordMarketSale(island.islandUuid(), itemId, dateKey, amount, factors.serverDemandFactor());
        if (debtRepaid > 0) {
            database.addLedger(island.islandUuid(), "MARKET_DEBT_REPAY", debtRepaid, itemId + " x" + amount);
        }
        return Optional.of(new SellResult(gross, paid, debtRepaid, factors.serverDemandFactor(), factors.personalFactor(), factors.qualityFactor()));
    }

    private void loadPersonalTiers(FileConfiguration config) {
        for (Map<?, ?> tier : config.getMapList("market.personal-soft-cap.tiers")) {
            Object amountValue = tier.get("amount");
            Object factorValue = tier.get("factor");
            if (amountValue == null || factorValue == null) {
                continue;
            }
            long amount = asLong(amountValue, 0);
            double factor = asDouble(factorValue, 1.0);
            if (amount > 0 && factor > 0) {
                personalTiers.add(new PriceCalculator.PersonalTier(amount, factor));
            }
        }
        personalTiers.sort(Comparator.comparingLong(PriceCalculator.PersonalTier::amount));
    }

    private void loadQualityFactors(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("market.quality-factor.tags");
        if (section == null) {
            tagQualityFactors.put("quality", 1.15);
            return;
        }
        for (String tag : section.getKeys(false)) {
            tagQualityFactors.put(tag.toLowerCase(), Math.max(0.0, section.getDouble(tag, 1.0)));
        }
    }

    private long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String dateKey() {
        return TimeUtil.todayKey();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private PriceCalculator calculator() {
        return new PriceCalculator(
                items,
                prices,
                targetDailyAmounts,
                itemQualityFactors,
                tagQualityFactors,
                personalTiers,
                personalSoftCapEnabled,
                personalSoftCap,
                demandFloor,
                demandCeiling,
                demandExponent
        );
    }
}
