package kr.seungmin.satisskyfactory.market;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final List<PersonalTier> personalTiers = new ArrayList<>();
    private boolean personalSoftCapEnabled = true;
    private int personalSoftCap = 256;
    private double demandFloor = 0.35;
    private double demandCeiling = 1.25;
    private double demandExponent = 0.35;
    private double debtRepayRate = 0.35;
    private double lockedDebtRepayRate = 0.70;

    public MarketService(StorageService storage, EconomyService economy, DatabaseService database, ItemRegistry items) {
        this.storage = storage;
        this.economy = economy;
        this.database = database;
        this.items = items;
    }

    public void load(FileConfiguration config) {
        prices.clear();
        targetDailyAmounts.clear();
        itemQualityFactors.clear();
        tagQualityFactors.clear();
        personalTiers.clear();
        personalSoftCapEnabled = config.getBoolean("market.personal-soft-cap.enabled", true);
        personalSoftCap = config.isInt("market.personal-soft-cap") ? config.getInt("market.personal-soft-cap", 256) : 256;
        demandFloor = config.getDouble("market.factor-min", config.getDouble("market.demand-floor", 0.35));
        demandCeiling = config.getDouble("market.factor-max", config.getDouble("market.demand-ceiling", 1.25));
        demandExponent = config.getDouble("market.demand-exponent", 0.35);
        debtRepayRate = config.getDouble("market.debt-repay-rate", 0.35);
        lockedDebtRepayRate = config.getDouble("market.locked-debt-repay-rate", 0.70);
        loadPersonalTiers(config);
        loadQualityFactors(config);
        ConfigurationSection items = config.getConfigurationSection("market.items");
        if (items == null) {
            return;
        }
        for (String itemId : items.getKeys(false)) {
            prices.put(itemId, items.getLong(itemId + ".base-price", 1));
            targetDailyAmounts.put(itemId, items.getLong(itemId + ".target-daily-amount", Math.max(1, personalSoftCap * 4L)));
            if (items.contains(itemId + ".quality-factor")) {
                itemQualityFactors.put(itemId, Math.max(0.0, items.getDouble(itemId + ".quality-factor", 1.0)));
            }
        }
    }

    public long price(String itemId, long amount) {
        return Math.max(0, Math.round(prices.getOrDefault(itemId, 0L) * amount));
    }

    public long price(UUID islandUuid, String itemId, long amount) {
        Factors factors = factors(islandUuid, itemId, amount);
        return Math.max(0, Math.round(prices.getOrDefault(itemId, 0L) * amount
                * factors.serverDemandFactor() * factors.personalFactor() * factors.qualityFactor()));
    }

    public Optional<SellResult> sell(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        if (amount <= 0 || !prices.containsKey(itemId)) {
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
        if (amount <= 0 || !prices.containsKey(itemId)) {
            return Optional.empty();
        }
        Optional<SellResult> result = payout(island, owner, itemId, amount);
        result.ifPresent(sale -> database.addLedger(island.islandUuid(), "MARKET_SELL_HAND", sale.gross(), itemId + " x" + amount));
        return result;
    }

    public Map<String, Long> prices() {
        return Map.copyOf(prices);
    }

    private Optional<SellResult> payout(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        Factors factors = factors(island.islandUuid(), itemId, amount);
        long gross = Math.max(0, Math.round(prices.getOrDefault(itemId, 0L) * amount
                * factors.serverDemandFactor() * factors.personalFactor() * factors.qualityFactor()));
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
        String dateKey = dateKey();
        database.recordMarketSale(island.islandUuid(), itemId, dateKey, amount, factors.serverDemandFactor());
        if (debtRepaid > 0) {
            database.addLedger(island.islandUuid(), "MARKET_DEBT_REPAY", debtRepaid, itemId + " x" + amount);
        }
        return Optional.of(new SellResult(gross, paid, debtRepaid, factors.serverDemandFactor(), factors.personalFactor(), factors.qualityFactor()));
    }

    private Factors factors(UUID islandUuid, String itemId, long amount) {
        String dateKey = dateKey();
        long serverSold = database.marketDailySold(itemId, dateKey);
        long personalSold = database.marketPersonalSold(islandUuid, itemId, dateKey);
        double target = Math.max(1.0, targetDailyAmounts.getOrDefault(itemId, Math.max(1, personalSoftCap * 4L)));
        double serverFactor = Math.pow(target / Math.max(1.0, serverSold + amount), demandExponent);
        serverFactor = clamp(serverFactor, demandFloor, demandCeiling);
        double personalFactor = personalFactor(personalSold + amount);
        return new Factors(serverFactor, personalFactor, qualityFactor(itemId));
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
                personalTiers.add(new PersonalTier(amount, factor));
            }
        }
        personalTiers.sort(Comparator.comparingLong(PersonalTier::amount));
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

    private double qualityFactor(String itemId) {
        Double itemFactor = itemQualityFactors.get(itemId);
        if (itemFactor != null) {
            return itemFactor;
        }
        return items.get(itemId)
                .map(item -> item.tags().stream()
                        .map(tag -> tagQualityFactors.getOrDefault(tag.toLowerCase(), 1.0))
                        .max(Double::compareTo)
                        .orElse(1.0))
                .orElse(1.0);
    }

    private double personalFactor(long totalSold) {
        if (!personalSoftCapEnabled) {
            return 1.0;
        }
        if (!personalTiers.isEmpty()) {
            return personalTiers.stream()
                    .filter(tier -> totalSold <= tier.amount())
                    .findFirst()
                    .map(PersonalTier::factor)
                    .orElseGet(() -> personalTiers.get(personalTiers.size() - 1).factor());
        }
        return totalSold > personalSoftCap
                ? Math.max(demandFloor, (double) personalSoftCap / totalSold)
                : 1.0;
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
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Factors(double serverDemandFactor, double personalFactor, double qualityFactor) {
    }

    private record PersonalTier(long amount, double factor) {
    }
}
