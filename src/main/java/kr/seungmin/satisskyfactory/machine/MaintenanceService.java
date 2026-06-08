package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;

public final class MaintenanceService {
    private final MachineService machines;
    private final EconomyService economy;
    private final DatabaseService database;
    private long intervalMillis;
    private long baseCost;
    private long perMachineCost;
    private long limitedThreshold;
    private long lockedThreshold;
    private long debtCap;

    public MaintenanceService(MachineService machines, EconomyService economy, DatabaseService database) {
        this.machines = machines;
        this.economy = economy;
        this.database = database;
    }

    public void load(FileConfiguration config) {
        intervalMillis = config.getLong("maintenance.interval-hours", 24) * 60L * 60L * 1000L;
        baseCost = config.getLong("maintenance.base-cost", 100);
        perMachineCost = config.getLong("maintenance.per-machine-cost", 8);
        limitedThreshold = config.getLong("maintenance.limited-threshold", 500);
        lockedThreshold = config.getLong("maintenance.locked-threshold", 1500);
        debtCap = config.getLong("maintenance.debt-cap", 5000);
    }

    public long chargeIfDue(FactoryIsland island, OfflinePlayer owner) {
        long now = Instant.now().toEpochMilli();
        if (island.lastMaintenanceAt() > 0 && now - island.lastMaintenanceAt() < intervalMillis) {
            updateStatus(island);
            return 0;
        }
        long due = baseCost + perMachineCost * machines.byIsland(island.islandUuid()).size();
        if (!economy.withdraw(owner, due)) {
            island.maintenanceDebt(Math.min(debtCap, island.maintenanceDebt() + due));
        } else {
            database.addLedger(island.islandUuid(), "MAINTENANCE", -due, "daily maintenance");
        }
        island.lastMaintenanceAt(now);
        updateStatus(island);
        return due;
    }

    public void setDebt(FactoryIsland island, long debt) {
        island.maintenanceDebt(Math.max(0, Math.min(debtCap, debt)));
        updateStatus(island);
    }

    public void updateStatus(FactoryIsland island) {
        if (island.maintenanceDebt() >= lockedThreshold) {
            island.maintenanceStatus(MaintenanceStatus.LOCKED);
        } else if (island.maintenanceDebt() >= limitedThreshold) {
            island.maintenanceStatus(MaintenanceStatus.LIMITED);
        } else {
            island.maintenanceStatus(MaintenanceStatus.NORMAL);
        }
    }
}
