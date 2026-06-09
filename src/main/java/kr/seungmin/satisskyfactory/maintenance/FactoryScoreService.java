package kr.seungmin.satisskyfactory.maintenance;

import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;

import java.util.UUID;

public final class FactoryScoreService {
    private final MachineService machines;

    public FactoryScoreService(MachineService machines) {
        this.machines = machines;
    }

    public long factoryScore(UUID islandUuid, int islandTier) {
        return machines.factoryScore(islandUuid, islandTier);
    }

    public long maintenanceScore(UUID islandUuid) {
        return machines.maintenanceScore(islandUuid);
    }

    public long refreshFactoryScore(FactoryIsland island) {
        long score = factoryScore(island.islandUuid(), island.tier());
        island.factoryScore(score);
        return score;
    }
}
