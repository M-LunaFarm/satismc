package kr.example.satisskyfactory.model;

import java.util.UUID;

public final class FactoryIsland {
    private final UUID islandUuid;
    private UUID ownerUuid;
    private int tier;
    private long researchPoints;
    private long reputation;
    private long maintenanceDebt;
    private MaintenanceStatus maintenanceStatus;
    private long factoryScore;
    private long lastMaintenanceAt;
    private long lastTickAt;
    private int emergencyContractsUsedToday;

    public FactoryIsland(UUID islandUuid, UUID ownerUuid) {
        this.islandUuid = islandUuid;
        this.ownerUuid = ownerUuid;
        this.tier = 1;
        this.maintenanceStatus = MaintenanceStatus.NORMAL;
    }

    public UUID islandUuid() {
        return islandUuid;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public void ownerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public int tier() {
        return tier;
    }

    public void tier(int tier) {
        this.tier = tier;
    }

    public long researchPoints() {
        return researchPoints;
    }

    public void researchPoints(long researchPoints) {
        this.researchPoints = researchPoints;
    }

    public long reputation() {
        return reputation;
    }

    public void reputation(long reputation) {
        this.reputation = reputation;
    }

    public long maintenanceDebt() {
        return maintenanceDebt;
    }

    public void maintenanceDebt(long maintenanceDebt) {
        this.maintenanceDebt = maintenanceDebt;
    }

    public MaintenanceStatus maintenanceStatus() {
        return maintenanceStatus;
    }

    public void maintenanceStatus(MaintenanceStatus maintenanceStatus) {
        this.maintenanceStatus = maintenanceStatus;
    }

    public long factoryScore() {
        return factoryScore;
    }

    public void factoryScore(long factoryScore) {
        this.factoryScore = factoryScore;
    }

    public long lastMaintenanceAt() {
        return lastMaintenanceAt;
    }

    public void lastMaintenanceAt(long lastMaintenanceAt) {
        this.lastMaintenanceAt = lastMaintenanceAt;
    }

    public long lastTickAt() {
        return lastTickAt;
    }

    public void lastTickAt(long lastTickAt) {
        this.lastTickAt = lastTickAt;
    }

    public int emergencyContractsUsedToday() {
        return emergencyContractsUsedToday;
    }

    public void emergencyContractsUsedToday(int emergencyContractsUsedToday) {
        this.emergencyContractsUsedToday = emergencyContractsUsedToday;
    }
}
