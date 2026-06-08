package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class MaintenanceService {
    private final MachineService machines;
    private final EconomyService economy;
    private final DatabaseService database;
    private long intervalMillis;
    private long baseCost;
    private long perMachineCost;
    private long minFee;
    private double exponent;
    private boolean exponentialFormula;
    private long newIslandFreeMillis;
    private long debtCapDays;
    private long dormantAfterMillis;
    private long warningThreshold;
    private long limitedThreshold;
    private long lockedThreshold;
    private double warningThresholdDays;
    private double limitedThresholdDays;
    private double lockedThresholdDays;
    private long debtCap;
    private Map<String, Long> repairCost;
    private Map<String, Long> brokenRepairCost;

    public MaintenanceService(MachineService machines, EconomyService economy, DatabaseService database) {
        this.machines = machines;
        this.economy = economy;
        this.database = database;
    }

    public void load(FileConfiguration config) {
        intervalMillis = config.getLong("maintenance.charge-interval-hours",
                config.getLong("maintenance.interval-hours", 24)) * 60L * 60L * 1000L;
        baseCost = config.getLong("maintenance.base-fee", config.getLong("maintenance.base-cost", 100));
        perMachineCost = config.getLong("maintenance.per-machine-cost", 8);
        minFee = config.getLong("maintenance.min-fee", 0);
        exponent = Math.max(0.1, config.getDouble("maintenance.exponent", 1.0));
        exponentialFormula = config.getString("maintenance.formula", "LINEAR_SCORE").equalsIgnoreCase("EXPONENTIAL_SCORE");
        newIslandFreeMillis = Math.max(0, config.getLong("maintenance.new-island-free-days", 0))
                * 24L * 60L * 60L * 1000L;
        debtCapDays = Math.max(0, config.getLong("maintenance.debt-cap-days", 0));
        dormantAfterMillis = Math.max(0, config.getLong("maintenance.dormant.stop-debt-growth-after-days-offline",
                config.getLong("maintenance.dormant-days", 0))) * 24L * 60L * 60L * 1000L;
        warningThreshold = config.getLong("maintenance.warning-threshold", 1);
        limitedThreshold = config.getLong("maintenance.limited-threshold", 500);
        lockedThreshold = config.getLong("maintenance.locked-threshold", 1500);
        warningThresholdDays = config.getDouble("maintenance.status-thresholds.warning-days", -1.0);
        limitedThresholdDays = config.getDouble("maintenance.status-thresholds.limited-days", -1.0);
        lockedThresholdDays = config.getDouble("maintenance.status-thresholds.locked-days", -1.0);
        debtCap = config.getLong("maintenance.debt-cap", 5000);
        repairCost = readCost(config.getConfigurationSection("maintenance.repair-cost"));
        brokenRepairCost = readCost(config.getConfigurationSection("maintenance.broken-repair-cost"));
    }

    public long chargeIfDue(FactoryIsland island, OfflinePlayer owner, Object rawIsland) {
        long now = Instant.now().toEpochMilli();
        if (island.lastMaintenanceAt() > 0 && now - island.lastMaintenanceAt() < intervalMillis) {
            island.factoryScore(machines.factoryScore(island.islandUuid(), island.tier()));
            updateStatus(island);
            return 0;
        }
        island.factoryScore(machines.factoryScore(island.islandUuid(), island.tier()));
        if (isNewIslandGracePeriod(island, now)) {
            updateStatus(island);
            return 0;
        }
        if (shouldDormant(island, now)) {
            island.maintenanceStatus(MaintenanceStatus.DORMANT);
            return 0;
        }
        long due = maintenanceFee(island);
        double paid = economy.withdrawMaintenance(owner, rawIsland, due);
        long shortage = Math.max(0, due - Math.round(paid));
        if (shortage > 0) {
            island.maintenanceDebt(Math.min(debtLimit(due), island.maintenanceDebt() + shortage));
        } else {
            island.maintenanceDebt(Math.max(0, island.maintenanceDebt() - Math.round(paid - due)));
        }
        if (paid > 0) {
            database.addLedger(island.islandUuid(), "MAINTENANCE", -Math.round(paid), "daily maintenance via " + economy.name());
        }
        island.lastMaintenanceAt(now);
        updateStatus(island);
        return due;
    }

    public void setDebt(FactoryIsland island, long debt) {
        island.factoryScore(machines.factoryScore(island.islandUuid(), island.tier()));
        island.maintenanceDebt(Math.max(0, Math.min(debtLimit(maintenanceFee(island)), debt)));
        updateStatus(island);
    }

    private long maintenanceFee(FactoryIsland island) {
        long score = Math.max(1, exponentialFormula ? island.factoryScore() : machines.maintenanceScore(island.islandUuid()));
        long calculated = exponentialFormula
                ? Math.round(baseCost * Math.pow(score, exponent))
                : baseCost + perMachineCost * score;
        return Math.max(minFee, calculated);
    }

    private long debtLimit(long fee) {
        if (debtCapDays <= 0) {
            return debtCap;
        }
        return Math.max(debtCap, fee * debtCapDays);
    }

    public void updateStatus(FactoryIsland island) {
        if (shouldDormant(island, Instant.now().toEpochMilli())) {
            island.maintenanceStatus(MaintenanceStatus.DORMANT);
            return;
        }
        Thresholds thresholds = thresholds(island);
        if (island.maintenanceDebt() >= thresholds.locked()) {
            island.maintenanceStatus(MaintenanceStatus.LOCKED);
        } else if (island.maintenanceDebt() >= thresholds.limited()) {
            island.maintenanceStatus(MaintenanceStatus.LIMITED);
        } else if (island.maintenanceDebt() >= thresholds.warning()) {
            island.maintenanceStatus(MaintenanceStatus.WARNING);
        } else {
            island.maintenanceStatus(MaintenanceStatus.NORMAL);
        }
    }

    private Thresholds thresholds(FactoryIsland island) {
        long fee = maintenanceFee(island);
        long warning = thresholdAmount(fee, warningThresholdDays, warningThreshold);
        long limited = Math.max(warning, thresholdAmount(fee, limitedThresholdDays, limitedThreshold));
        long locked = Math.max(limited, thresholdAmount(fee, lockedThresholdDays, lockedThreshold));
        return new Thresholds(warning, limited, locked);
    }

    private long thresholdAmount(long fee, double days, long fallback) {
        if (days > 0.0 && fee > 0) {
            return Math.max(1L, Math.round(fee * days));
        }
        return Math.max(1L, fallback);
    }

    public Map<String, Long> repairCost(boolean broken) {
        return Map.copyOf(broken ? brokenRepairCost : repairCost);
    }

    private Map<String, Long> readCost(ConfigurationSection section) {
        Map<String, Long> result = new HashMap<>();
        if (section == null) {
            return result;
        }
        for (String itemId : section.getKeys(false)) {
            long amount = section.getLong(itemId, 0);
            if (amount > 0) {
                result.put(itemId, amount);
            }
        }
        return result;
    }

    private boolean shouldDormant(FactoryIsland island, long now) {
        return dormantAfterMillis > 0
                && island.maintenanceDebt() > 0
                && island.lastTickAt() > 0
                && now - island.lastTickAt() >= dormantAfterMillis;
    }

    private boolean isNewIslandGracePeriod(FactoryIsland island, long now) {
        return newIslandFreeMillis > 0
                && island.maintenanceDebt() <= 0
                && island.createdAt() > 0
                && now - island.createdAt() < newIslandFreeMillis;
    }

    private record Thresholds(long warning, long limited, long locked) {
    }
}
