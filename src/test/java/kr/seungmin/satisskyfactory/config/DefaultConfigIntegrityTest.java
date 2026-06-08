package kr.seungmin.satisskyfactory.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConfigIntegrityTest {
    @Test
    void pluginMetadataUsesSeungminPackageAndExpectedCommands() {
        YamlConfiguration plugin = load("plugin.yml");
        YamlConfiguration config = load("config.yml");
        YamlConfiguration messages = load("messages.yml");
        YamlConfiguration maintenance = load("maintenance.yml");

        assertEquals("SatisSkyFactory", plugin.getString("name"));
        assertEquals("kr.seungmin.satisskyfactory.SatisSkyFactoryPlugin", plugin.getString("main"));
        assertEquals(List.of("SuperiorSkyblock2"), plugin.getStringList("depend"));
        assertTrue(plugin.isConfigurationSection("commands.factory"));
        assertTrue(plugin.isConfigurationSection("commands.sfactory"));
        assertEquals(20, config.getInt("settings.tick-period-ticks"));
        assertEquals(300, config.getInt("settings.max-machines-per-tick"));
        assertEquals(60, config.getInt("settings.max-backfill-cycles"));
        assertTrue(config.getBoolean("settings.offline-production.enabled"));
        assertEquals(8, config.getInt("settings.offline-production.max-hours"));
        assertEquals(0.35, config.getDouble("settings.offline-production.efficiency"));
        assertEquals("VAULT_PLAYER", config.getString("economy.mode"));
        assertTrue(config.getBoolean("economy.use-vault"));
        assertEquals("원", config.getString("economy.currency-symbol"));
        assertEquals("SQLITE", config.getString("database.type"));
        assertEquals("data.db", config.getString("database.sqlite-file"));
        assertEquals(60, config.getInt("database.save-interval-seconds"));
        assertTrue(config.getBoolean("visuals.particles"));
        assertFalse(config.getBoolean("visuals.display-entities"));
        assertEquals(30, config.getInt("visuals.max-display-entities-per-island"));
        assertEquals(50, config.getInt("limits.base-machine-limit"));
        assertEquals(25, config.getInt("limits.machine-limit-per-island-tier"));
        assertEquals(20, config.getInt("limits.base-network-limit"));
        assertTrue(messages.getString("messages.machine-status", "").contains("{status}"));
        assertEquals(24, maintenance.getInt("maintenance.charge-interval-hours"));
        assertEquals(1, maintenance.getInt("maintenance.status-thresholds.warning-days"));
        assertEquals(3, maintenance.getInt("maintenance.status-thresholds.limited-days"));
        assertEquals(5, maintenance.getInt("maintenance.status-thresholds.locked-days"));
        assertFalse(maintenance.getBoolean("maintenance.locked.block-market-sales"));
        assertEquals(70, maintenance.getInt("maintenance.locked.auto-pay-debt-from-sales-percent"));
    }

    @Test
    void defaultConfigsReferenceOnlyKnownIds() {
        YamlConfiguration itemsConfig = load("items.yml");
        YamlConfiguration machinesConfig = load("machines.yml");
        YamlConfiguration recipesConfig = load("recipes.yml");
        YamlConfiguration nodesConfig = load("resource-nodes.yml");
        YamlConfiguration marketConfig = load("market.yml");
        YamlConfiguration contractsConfig = load("contracts.yml");
        YamlConfiguration researchConfig = load("research.yml");

        Set<String> items = keys(itemsConfig, "items");
        Set<String> machines = keys(machinesConfig, "machines");
        Set<String> recipes = keys(recipesConfig, "recipes");
        Set<String> nodes = keys(nodesConfig, "nodes");
        Set<String> contracts = keys(contractsConfig, "contracts.templates");
        Set<String> research = keys(researchConfig, "research.unlocks");
        List<String> issues = new ArrayList<>();

        for (String recipeId : recipes) {
            String base = "recipes." + recipeId + ".";
            for (String machineId : recipeMachines(recipesConfig, base)) {
                if (!machines.contains(machineId)) {
                    issues.add("recipe " + recipeId + " unknown machine " + machineId);
                }
            }
            for (String section : List.of("input", "inputs", "output", "outputs", "byproducts")) {
                for (String itemId : keys(recipesConfig, base + section)) {
                    if (!items.contains(itemId)) {
                        issues.add("recipe " + recipeId + " unknown item " + itemId + " in " + section);
                    }
                }
            }
            String qualityItem = recipesConfig.getString(base + "quality-item", "");
            if (!qualityItem.isBlank() && !items.contains(qualityItem)) {
                issues.add("recipe " + recipeId + " unknown quality item " + qualityItem);
            }
            for (String unlockId : stringList(recipesConfig, base + "research-required", base + "researchRequired")) {
                if (!research.contains(unlockId)) {
                    issues.add("recipe " + recipeId + " unknown research " + unlockId);
                }
            }
        }

        for (String machineId : machines) {
            String base = "machines." + machineId + ".";
            for (String recipeId : machinesConfig.getStringList(base + "allowed-recipes")) {
                if (!recipes.contains(recipeId)) {
                    issues.add("machine " + machineId + " unknown recipe " + recipeId);
                }
            }
            for (String unlockId : machinesConfig.getStringList(base + "required-unlocks")) {
                if (!research.contains(unlockId)) {
                    issues.add("machine " + machineId + " unknown research " + unlockId);
                }
            }
            for (String itemId : values(machinesConfig, base + "harvest-drops")) {
                if (!items.contains(itemId)) {
                    issues.add("machine " + machineId + " unknown harvest item " + itemId);
                }
            }
            for (String itemId : keys(machinesConfig, base + "planting")) {
                if (!items.contains(itemId)) {
                    issues.add("machine " + machineId + " unknown planting item " + itemId);
                }
            }
            for (String itemId : List.of(
                    machinesConfig.getString(base + "fertilizer.item", ""),
                    machinesConfig.getString(base + "fertilizer.quality-item", ""))) {
                if (!itemId.isBlank() && !items.contains(itemId)) {
                    issues.add("machine " + machineId + " unknown fertilizer item " + itemId);
                }
            }
        }

        for (String nodeId : nodes) {
            String itemId = nodesConfig.getString("nodes." + nodeId + ".resource-id",
                    nodesConfig.getString("nodes." + nodeId + ".resource", ""));
            if (!itemId.isBlank() && !items.contains(itemId)) {
                issues.add("node " + nodeId + " unknown item " + itemId);
            }
        }

        for (String itemId : keys(marketConfig, "market.items")) {
            if (!items.contains(itemId)) {
                issues.add("market unknown item " + itemId);
                continue;
            }
            long marketPrice = marketConfig.getLong("market.items." + itemId + ".base-price", 0);
            long itemPrice = itemsConfig.getLong("items." + itemId + ".base-price", 0);
            if (marketPrice <= 0 && itemPrice <= 0) {
                issues.add("market item " + itemId + " has no positive base price");
            }
            if (marketConfig.getLong("market.items." + itemId + ".target-daily-amount", 0) <= 0) {
                issues.add("market item " + itemId + " has no positive target daily amount");
            }
        }

        for (String contractId : contracts) {
            String base = "contracts.templates." + contractId + ".";
            for (String itemId : keys(contractsConfig, base + "required")) {
                if (!items.contains(itemId)) {
                    issues.add("contract " + contractId + " unknown item " + itemId);
                }
            }
            for (String itemId : keys(contractsConfig, base + "rewards.items")) {
                if (!items.contains(itemId)) {
                    issues.add("contract " + contractId + " unknown reward item " + itemId);
                }
            }
        }

        for (String unlockId : research) {
            String base = "research.unlocks." + unlockId + ".";
            for (String required : stringList(researchConfig, base + "required-unlocks", base + "requires")) {
                if (!research.contains(required)) {
                    issues.add("research " + unlockId + " unknown prerequisite " + required);
                }
            }
            for (String grant : researchConfig.getStringList(base + "unlocks")) {
                if (!machines.contains(grant) && !recipes.contains(grant) && !items.contains(grant)) {
                    issues.add("research " + unlockId + " grant has no target " + grant);
                }
            }
        }

        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test
    void mvpCoreMachinesAndRecipesAreAvailableAtTierOne() {
        YamlConfiguration machines = load("machines.yml");
        YamlConfiguration recipes = load("recipes.yml");
        List<String> issues = new ArrayList<>();

        for (String machineId : List.of(
                "harvester_t1", "miner_drill_t1", "grinder_t1", "furnace_t1", "assembler_t1",
                "packager_t1", "bio_generator_t1", "battery_t1", "conveyor_t1", "storage_t1")) {
            String base = "machines." + machineId + ".";
            if (!machines.isConfigurationSection(base.substring(0, base.length() - 1))) {
                issues.add("missing core machine " + machineId);
                continue;
            }
            if (machines.getInt(base + "tier", 1) != 1) {
                issues.add(machineId + " tier is " + machines.getInt(base + "tier", 1));
            }
            if (!machines.getStringList(base + "required-unlocks").isEmpty()) {
                issues.add(machineId + " has required unlocks");
            }
        }

        for (String recipeId : List.of(
                "biofuel_to_power", "grind_wheat", "grind_iron", "make_feed",
                "smelt_crushed_iron", "assemble_plate", "assemble_machine_parts", "package_bread")) {
            String base = "recipes." + recipeId + ".";
            if (!recipes.isConfigurationSection(base.substring(0, base.length() - 1))) {
                issues.add("missing core recipe " + recipeId);
                continue;
            }
            if (recipes.getInt(base + "min-tier", recipes.getInt(base + "minTier", 1)) != 1) {
                issues.add(recipeId + " min-tier mismatch");
            }
            if (!stringList(recipes, base + "research-required", base + "researchRequired").isEmpty()) {
                issues.add(recipeId + " has required research");
            }
        }

        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    private YamlConfiguration load(String name) {
        File file = new File("src/main/resources", name);
        assertTrue(file.isFile(), name + " is missing");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        assertFalse(config.getKeys(false).isEmpty(), name + " is empty");
        return config;
    }

    private Set<String> keys(YamlConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? Set.of() : section.getKeys(false);
    }

    private List<String> values(YamlConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream()
                .map(key -> section.getString(key, ""))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> recipeMachines(YamlConfiguration config, String base) {
        List<String> machines = new ArrayList<>(config.getStringList(base + "machines"));
        String single = config.getString(base + "machine", "");
        if (machines.isEmpty() && single != null && !single.isBlank()) {
            machines.add(single);
        }
        return machines;
    }

    private List<String> stringList(YamlConfiguration config, String firstPath, String secondPath) {
        List<String> values = new ArrayList<>(config.getStringList(firstPath));
        if (values.isEmpty()) {
            values.addAll(config.getStringList(secondPath));
        }
        if (values.isEmpty()) {
            String scalarPath = config.isString(firstPath) ? firstPath : secondPath;
            if (config.isString(scalarPath)) {
                String scalar = config.getString(scalarPath, "");
                if (scalar != null && !scalar.isBlank()) {
                    values.add(scalar);
                }
            }
        }
        return values;
    }
}
