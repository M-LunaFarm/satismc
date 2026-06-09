package kr.seungmin.satisskyfactory.contract;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ContractService {
    public record ActiveContract(Contract contract, ContractTemplate template) {
        public UUID contractId() {
            return contract.contractId();
        }

        public long expiresAt() {
            return contract.expiresAt();
        }
    }

    private final StorageService storage;
    private final EconomyService economy;
    private final DatabaseService database;
    private final IslandBoostService boosts;
    private final Map<String, ContractTemplate> templates = new HashMap<>();
    private ContractTemplate emergency;
    private int dailySlots;
    private int weeklySlots;
    private int storySlots;
    private int marketSlots;
    private int emergencyDailyLimit;
    private Set<String> boostedSlotTypes = Set.of("DAILY", "WEEKLY", "STORY", "MARKET");

    public ContractService(StorageService storage, EconomyService economy, DatabaseService database, IslandBoostService boosts) {
        this.storage = storage;
        this.economy = economy;
        this.database = database;
        this.boosts = boosts;
    }

    public void load(FileConfiguration config) {
        templates.clear();
        emergency = null;
        dailySlots = Math.max(1, config.getInt("contracts.daily_slots",
                config.getInt("contracts.daily-slots-base", 3)));
        weeklySlots = Math.max(0, config.getInt("contracts.weekly_slots",
                config.getInt("contracts.weekly-slots-base", 1)));
        storySlots = Math.max(0, config.getInt("contracts.story_slots",
                config.getInt("contracts.story-slots-base", 0)));
        marketSlots = Math.max(0, config.getInt("contracts.market_slots",
                config.getInt("contracts.market-slots-base", 0)));
        emergencyDailyLimit = Math.max(1, config.getInt("contracts.emergency-daily-limit",
                config.getInt("contracts.emergency_daily_limit", 5)));
        boostedSlotTypes = boostedSlotTypes(config);
        ConfigurationSection section = config.getConfigurationSection("contracts.templates");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                templates.put(id, template(config, "contracts.templates." + id + ".", id));
            }
        }
        emergency = config.isConfigurationSection("contracts.emergency")
                ? template(config, "contracts.emergency.", "emergency")
                : emergencyTemplates().stream().findFirst().orElse(null);
    }

    public List<ActiveContract> activeContracts(FactoryIsland island) {
        expireOldContracts(island);
        ensureDailyContracts(island);
        return database.loadContracts(island.islandUuid(), "ACTIVE").stream()
                .map(this::contract)
                .map(contract -> new ActiveContract(contract, templates.get(contract.templateId())))
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

    public Optional<ActiveContract> completeContract(FactoryIsland island, OfflinePlayer owner, UUID contractId) {
        return activeContracts(island).stream()
                .filter(active -> active.contractId().equals(contractId))
                .filter(active -> complete(island, owner, active))
                .findFirst();
    }

    public boolean completeEmergency(FactoryIsland island, OfflinePlayer owner) {
        if (island.maintenanceDebt() <= 0) {
            return false;
        }
        int usedToday = database.countContracts(island.islandUuid(), "EMERGENCY", "COMPLETED", startOfToday());
        island.emergencyContractsUsedToday(usedToday);
        if (usedToday >= emergencyDailyLimit) {
            database.saveIsland(island);
            return false;
        }
        for (ContractTemplate template : emergencyTemplates()) {
            boolean completed = complete(island, owner, new ActiveContract(
                    contract(UUID.randomUUID(), island.islandUuid(), template, "{}",
                            Contract.Status.ACTIVE, TimeUtil.hoursFromNowMillis(template.expiresHours() > 0 ? template.expiresHours() : 6)),
                    template
            ));
            if (completed) {
                island.emergencyContractsUsedToday(usedToday + 1);
                database.saveIsland(island);
                return true;
            }
        }
        return false;
    }

    public Optional<ContractTemplate> emergencyTemplate() {
        return emergencyTemplates().stream().findFirst().or(() -> Optional.ofNullable(emergency));
    }

    public int emergencyUsedToday(FactoryIsland island) {
        return database.countContracts(island.islandUuid(), "EMERGENCY", "COMPLETED", startOfToday());
    }

    public int emergencyDailyLimit() {
        return emergencyDailyLimit;
    }

    public Map<String, ContractTemplate> templates() {
        return Map.copyOf(templates);
    }

    private List<ContractTemplate> emergencyTemplates() {
        List<ContractTemplate> configured = templates.values().stream()
                .filter(template -> template.type().equalsIgnoreCase("EMERGENCY"))
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        return emergency == null ? List.of() : List.of(emergency);
    }

    private void ensureDailyContracts(FactoryIsland island) {
        IslandBoostService.Boosts islandBoosts = boosts.boosts(island.islandUuid());
        ensureContracts(island, "DAILY", boostedSlots("DAILY", dailySlots, islandBoosts), 24);
        ensureContracts(island, "WEEKLY", boostedSlots("WEEKLY", weeklySlots, islandBoosts), 168);
        ensureContracts(island, "STORY", boostedSlots("STORY", storySlots, islandBoosts), 168);
        ensureContracts(island, "MARKET", boostedSlots("MARKET", marketSlots, islandBoosts), 24);
    }

    private int boostedSlots(String type, int baseSlots, IslandBoostService.Boosts islandBoosts) {
        if (baseSlots <= 0) {
            return 0;
        }
        String normalized = type.toUpperCase(java.util.Locale.ROOT);
        int bonus = boostedSlotTypes.contains(normalized) ? islandBoosts.contractSlotBonus() : 0;
        return Math.max(0, baseSlots + bonus);
    }

    private void ensureContracts(FactoryIsland island, String type, int slots, long defaultExpiresHours) {
        if (slots <= 0) {
            return;
        }
        long expiresAt = TimeUtil.hoursFromNowMillis(defaultExpiresHours);
        int activeCount = (int) database.loadContracts(island.islandUuid(), "ACTIVE").stream()
                .map(stored -> templates.get(stored.templateId()))
                .filter(template -> template != null && template.type().equalsIgnoreCase(type))
                .count();
        if (activeCount >= slots) {
            return;
        }
        for (ContractTemplate template : templates.values().stream().sorted((left, right) -> left.id().compareTo(right.id())).toList()) {
            if (activeCount >= slots) {
                return;
            }
            if (!template.type().equalsIgnoreCase(type)) {
                continue;
            }
            if (!matchesTier(island, template)) {
                continue;
            }
            if (database.hasContractForTemplate(island.islandUuid(), template.id(), "ACTIVE")) {
                continue;
            }
            database.saveContract(storedContract(contract(
                    UUID.randomUUID(),
                    island.islandUuid(),
                    template,
                    "{}",
                    Contract.Status.ACTIVE,
                    template.expiresHours() > 0
                            ? TimeUtil.hoursFromNowMillis(template.expiresHours())
                            : expiresAt
            )));
            activeCount++;
        }
    }

    private void expireOldContracts(FactoryIsland island) {
        long now = TimeUtil.nowMillis();
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
        long netSpace = template.itemRewards().values().stream().mapToLong(Long::longValue).sum()
                - template.required().values().stream().mapToLong(Long::longValue).sum();
        if (netSpace > 0 && inventory.used() + netSpace > inventory.capacity()) {
            return false;
        }
        template.required().forEach((item, amount) -> inventory.remove(item, amount));
        template.itemRewards().forEach(inventory::add);
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
        database.saveContract(storedContract(active.contract().completed(template.required())));
        return true;
    }

    private boolean matchesTier(FactoryIsland island, ContractTemplate template) {
        return template.tier() <= island.tier()
                && (template.maxTier() <= 0 || island.tier() <= template.maxTier());
    }

    private ContractTemplate template(FileConfiguration config, String base, String id) {
        return new ContractTemplate(
                id,
                config.getString(base + "type", "DAILY"),
                config.getInt(base + "tier", config.getInt(base + "min-tier", 1)),
                config.getInt(base + "max-tier", config.getInt(base + "max_tier", 0)),
                map(config.getConfigurationSection(base + "required")),
                config.getLong(base + "rewards.money", 0),
                config.getLong(base + "rewards.research",
                        config.getLong(base + "rewards.research-points", 0)),
                config.getLong(base + "rewards.reputation", 0),
                config.getLong(base + "rewards.debt-relief",
                        config.getLong(base + "rewards.debt-payment", 0)),
                map(config.getConfigurationSection(base + "rewards.items")),
                config.getLong(base + "expires-hours", defaultExpiresHours(config.getString(base + "type", "DAILY")))
        );
    }

    private long defaultExpiresHours(String type) {
        if (type == null) {
            return 24;
        }
        if (type.equalsIgnoreCase("WEEKLY")) {
            return 168;
        }
        if (type.equalsIgnoreCase("EMERGENCY")) {
            return 6;
        }
        if (type.equalsIgnoreCase("STORY")) {
            return 168;
        }
        if (type.equalsIgnoreCase("MARKET")) {
            return 24;
        }
        return 24;
    }

    private Set<String> boostedSlotTypes(FileConfiguration config) {
        List<String> values = config.getStringList("contracts.boosted-slot-types");
        if (values.isEmpty()) {
            values = config.getStringList("contracts.boosted_slot_types");
        }
        if (values.isEmpty()) {
            return Set.of("DAILY", "WEEKLY", "STORY", "MARKET");
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.toUpperCase(java.util.Locale.ROOT));
            }
        }
        return normalized.isEmpty() ? Set.of("DAILY", "WEEKLY", "STORY", "MARKET") : Set.copyOf(normalized);
    }

    private Map<String, Long> rewards(ContractTemplate template) {
        Map<String, Long> rewards = new HashMap<>();
        rewards.put("money", template.money());
        rewards.put("research", template.research());
        rewards.put("reputation", template.reputation());
        rewards.put("debt-relief", template.debtRelief());
        template.itemRewards().forEach((item, amount) -> rewards.put("item:" + item, amount));
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

    private Contract contract(UUID contractId, UUID islandUuid, ContractTemplate template, String progressJson,
                              Contract.Status status, long expiresAt) {
        return new Contract(
                contractId,
                islandUuid,
                template.id(),
                template.tier(),
                Contract.Type.fromStoredValue(template.type()),
                template.required(),
                rewardsModel(template),
                map(progressJson),
                status,
                expiresAt
        );
    }

    private Contract contract(DatabaseService.StoredContract stored) {
        return new Contract(
                stored.contractId(),
                stored.islandUuid(),
                stored.templateId(),
                stored.tier(),
                Contract.Type.fromStoredValue(stored.contractType()),
                map(stored.requiredJson()),
                rewardsModel(stored.rewardsJson()),
                map(stored.progressJson()),
                Contract.Status.fromStoredValue(stored.status()),
                stored.expiresAt()
        );
    }

    private DatabaseService.StoredContract storedContract(Contract contract) {
        return new DatabaseService.StoredContract(
                contract.contractId(),
                contract.islandUuid(),
                contract.templateId(),
                contract.type().name(),
                contract.tier(),
                json(contract.requiredItems()),
                json(contract.progress()),
                json(rewards(contract.rewards())),
                contract.status().name(),
                contract.expiresAt()
        );
    }

    private Contract.Rewards rewardsModel(ContractTemplate template) {
        return new Contract.Rewards(template.money(), template.research(), template.reputation(),
                template.debtRelief(), template.itemRewards());
    }

    private Contract.Rewards rewardsModel(String rewardsJson) {
        Map<String, Long> values = map(rewardsJson);
        Map<String, Long> items = new HashMap<>();
        values.forEach((key, value) -> {
            if (key.startsWith("item:")) {
                items.put(key.substring("item:".length()), value);
            }
        });
        return new Contract.Rewards(
                values.getOrDefault("money", 0L),
                values.getOrDefault("research", 0L),
                values.getOrDefault("reputation", 0L),
                values.getOrDefault("debt-relief", 0L),
                items
        );
    }

    private Map<String, Long> rewards(Contract.Rewards rewards) {
        Map<String, Long> values = new HashMap<>();
        values.put("money", rewards.money());
        values.put("research", rewards.researchPoints());
        values.put("reputation", rewards.reputation());
        values.put("debt-relief", rewards.debtRelief());
        rewards.items().forEach((item, amount) -> values.put("item:" + item, amount));
        return values;
    }

    private Map<String, Long> map(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Map.of();
        }
        Map<String, Long> values = new HashMap<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return Map.of();
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return Map.of();
        }
        for (String part : body.split(",")) {
            int colon = valueSeparator(part);
            if (colon < 0) {
                continue;
            }
            String key = part.substring(0, colon).trim();
            if (key.startsWith("\"") && key.endsWith("\"") && key.length() >= 2) {
                key = key.substring(1, key.length() - 1);
            }
            try {
                values.put(key, Long.parseLong(part.substring(colon + 1).trim()));
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy fields instead of failing all contract loading.
            }
        }
        return values;
    }

    private int valueSeparator(String entry) {
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < entry.length(); index++) {
            char current = entry.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                quoted = !quoted;
            } else if (current == ':' && !quoted) {
                return index;
            }
        }
        return -1;
    }

    private long startOfToday() {
        return TimeUtil.startOfTodayMillis();
    }
}
