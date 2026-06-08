package kr.seungmin.satisskyfactory.contract;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ContractService {
    public record ContractTemplate(String id, String type, Map<String, Long> required, long money, long research, long reputation, long debtRelief) {
    }

    private final StorageService storage;
    private final EconomyService economy;
    private final DatabaseService database;
    private final Map<String, ContractTemplate> templates = new HashMap<>();
    private ContractTemplate emergency;

    public ContractService(StorageService storage, EconomyService economy, DatabaseService database) {
        this.storage = storage;
        this.economy = economy;
        this.database = database;
    }

    public void load(FileConfiguration config) {
        templates.clear();
        ConfigurationSection section = config.getConfigurationSection("contracts.templates");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                templates.put(id, template(config, "contracts.templates." + id + ".", id));
            }
        }
        emergency = template(config, "contracts.emergency.", "emergency");
    }

    public Optional<ContractTemplate> completeAny(FactoryIsland island, OfflinePlayer owner) {
        for (ContractTemplate template : templates.values()) {
            if (complete(island, owner, template)) {
                return Optional.of(template);
            }
        }
        return Optional.empty();
    }

    public boolean completeEmergency(FactoryIsland island, OfflinePlayer owner) {
        return emergency != null && complete(island, owner, emergency);
    }

    public Map<String, ContractTemplate> templates() {
        return Map.copyOf(templates);
    }

    private boolean complete(FactoryIsland island, OfflinePlayer owner, ContractTemplate template) {
        VirtualInventory inventory = storage.islandStorage(island.islandUuid());
        if (!template.required().entrySet().stream().allMatch(entry -> inventory.amount(entry.getKey()) >= entry.getValue())) {
            return false;
        }
        template.required().forEach((item, amount) -> inventory.remove(item, amount));
        storage.save(inventory);
        if (template.money() > 0) {
            economy.deposit(owner, template.money());
            database.addLedger(island.islandUuid(), "CONTRACT_REWARD", template.money(), template.id());
        }
        island.researchPoints(island.researchPoints() + template.research());
        island.reputation(island.reputation() + template.reputation());
        if (template.debtRelief() > 0) {
            island.maintenanceDebt(Math.max(0, island.maintenanceDebt() - template.debtRelief()));
        }
        return true;
    }

    private ContractTemplate template(FileConfiguration config, String base, String id) {
        return new ContractTemplate(
                id,
                config.getString(base + "type", "DAILY"),
                map(config.getConfigurationSection(base + "required")),
                config.getLong(base + "rewards.money", 0),
                config.getLong(base + "rewards.research", 0),
                config.getLong(base + "rewards.reputation", 0),
                config.getLong(base + "rewards.debt-relief", 0)
        );
    }

    private Map<String, Long> map(ConfigurationSection section) {
        Map<String, Long> result = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(key, section.getLong(key));
            }
        }
        return result;
    }
}
