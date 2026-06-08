package kr.seungmin.satisskyfactory.contract;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ContractService {
    public record ContractTemplate(String id, String type, Map<String, Long> required, long money, long research, long reputation, long debtRelief) {
    }

    public record ActiveContract(UUID contractId, ContractTemplate template, long expiresAt) {
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

    public List<ActiveContract> activeContracts(FactoryIsland island) {
        ensureDailyContracts(island);
        expireOldContracts(island);
        return database.loadContracts(island.islandUuid(), "ACTIVE").stream()
                .map(stored -> new ActiveContract(stored.contractId(), templates.get(stored.templateId()), stored.expiresAt()))
                .filter(active -> active.template() != null)
                .toList();
    }

    public Optional<ActiveContract> completeAny(FactoryIsland island, OfflinePlayer owner) {
        for (ActiveContract active : activeContracts(island)) {
            if (complete(island, owner, active)) {
                return Optional.of(active);
            }
        }
        return Optional.empty();
    }

    public boolean completeEmergency(FactoryIsland island, OfflinePlayer owner) {
        if (emergency == null) {
            return false;
        }
        return complete(island, owner, new ActiveContract(UUID.randomUUID(), emergency, Instant.now().plus(Duration.ofHours(24)).toEpochMilli()));
    }

    public Map<String, ContractTemplate> templates() {
        return Map.copyOf(templates);
    }

    private void ensureDailyContracts(FactoryIsland island) {
        long expiresAt = Instant.now().plus(Duration.ofHours(24)).toEpochMilli();
        for (ContractTemplate template : templates.values()) {
            if (!template.type().equalsIgnoreCase("DAILY")) {
                continue;
            }
            if (database.hasContractForTemplate(island.islandUuid(), template.id(), "ACTIVE")) {
                continue;
            }
            database.saveContract(new DatabaseService.StoredContract(
                    UUID.randomUUID(),
                    island.islandUuid(),
                    template.id(),
                    template.type(),
                    1,
                    json(template.required()),
                    "{}",
                    json(rewards(template)),
                    "ACTIVE",
                    expiresAt
            ));
        }
    }

    private void expireOldContracts(FactoryIsland island) {
        long now = Instant.now().toEpochMilli();
        for (DatabaseService.StoredContract contract : database.loadContracts(island.islandUuid(), "ACTIVE")) {
            if (contract.expiresAt() > 0 && contract.expiresAt() < now) {
                database.updateContractStatus(contract.contractId(), "EXPIRED", contract.progressJson());
            }
        }
    }

    private boolean complete(FactoryIsland island, OfflinePlayer owner, ActiveContract active) {
        ContractTemplate template = active.template();
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
        database.saveIsland(island);
        database.updateContractStatus(active.contractId(), "COMPLETED", json(template.required()));
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

    private Map<String, Long> rewards(ContractTemplate template) {
        Map<String, Long> rewards = new HashMap<>();
        rewards.put("money", template.money());
        rewards.put("research", template.research());
        rewards.put("reputation", template.reputation());
        rewards.put("debt-relief", template.debtRelief());
        return rewards;
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

    private String json(Map<String, Long> values) {
        return values.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
