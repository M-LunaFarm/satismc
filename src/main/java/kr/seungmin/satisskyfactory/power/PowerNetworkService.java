package kr.seungmin.satisskyfactory.power;

import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.storage.StorageService;

import java.util.UUID;

public final class PowerNetworkService {
    private final MachineService machines;
    private final MachineDefinitionService definitions;
    private final StorageService storage;

    public PowerNetworkService(MachineService machines, MachineDefinitionService definitions, StorageService storage) {
        this.machines = machines;
        this.definitions = definitions;
        this.storage = storage;
    }

    public double powerRatio(UUID islandUuid) {
        double generation = 0;
        double consumption = 0;
        for (MachineInstance machine : machines.byIsland(islandUuid)) {
            MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
            if (definition == null) {
                continue;
            }
            if (definition.isGenerator()) {
                boolean hasFuel = storage.islandStorage(machine.islandUuid()).amount("biofuel") > 0;
                if (hasFuel) {
                    generation += definition.powerGeneration();
                }
            } else if (!definition.isBattery()) {
                consumption += definition.powerConsumption();
            }
        }
        if (consumption <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, generation / consumption));
    }
}
