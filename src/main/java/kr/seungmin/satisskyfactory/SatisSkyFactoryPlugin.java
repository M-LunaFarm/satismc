package kr.seungmin.satisskyfactory;

import kr.seungmin.satisskyfactory.command.FactoryCommand;
import kr.seungmin.satisskyfactory.config.ConfigService;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyModeFactory;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.PlaceholderHook;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.listener.FactoryLifecycleListener;
import kr.seungmin.satisskyfactory.listener.FactoryGuiListener;
import kr.seungmin.satisskyfactory.listener.MachineListener;
import kr.seungmin.satisskyfactory.logistics.ItemNetworkService;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import kr.seungmin.satisskyfactory.task.MachineTickService;
import kr.seungmin.satisskyfactory.task.MaintenanceTickService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public final class SatisSkyFactoryPlugin extends JavaPlugin {
    private ConfigService configs;
    private MessageService messages;
    private DatabaseService database;
    private SuperiorSkyblockHook skyblock;
    private EconomyService economy;
    private ItemRegistry itemRegistry;
    private CustomItemFactory itemFactory;
    private MachineDefinitionService machineDefinitions;
    private RecipeService recipes;
    private StorageService storage;
    private FactoryIslandService islands;
    private MachineService machines;
    private IslandBoostService boosts;
    private ResourceNodeService nodes;
    private ItemNetworkService itemNetworks;
    private PowerNetworkService power;
    private MarketService market;
    private ContractService contracts;
    private MaintenanceService maintenance;
    private ResearchService research;
    private FactoryGuiService gui;
    private DirtySaveService dirtySaves;
    private MachineTickService ticker;
    private MaintenanceTickService maintenanceTicker;
    private PlaceholderHook placeholderHook;

    @Override
    public void onEnable() {
        configs = new ConfigService(this);
        configs.load();
        messages = new MessageService(configs);

        skyblock = new SuperiorSkyblockHook(this);
        if (!skyblock.enable()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        configureSkyblockHook();

        database = new DatabaseService(this);
        database.open();

        economy = EconomyModeFactory.create(this, configs.main());
        itemRegistry = new ItemRegistry();
        itemFactory = new CustomItemFactory(this);
        machineDefinitions = new MachineDefinitionService();
        recipes = new RecipeService();
        storage = new StorageService(database, configInt("storage.default-capacity", "limits.default-storage-capacity", 10000));
        islands = new FactoryIslandService(skyblock, database);
        machines = new MachineService(database, machineDefinitions, storage);
        boosts = new IslandBoostService(skyblock);
        boosts.configure(configs.main());
        nodes = new ResourceNodeService(database);
        dirtySaves = new DirtySaveService(this, database);
        storage.dirtySaves(dirtySaves);
        islands.dirtySaves(dirtySaves);
        machines.dirtySaves(dirtySaves);
        nodes.dirtySaves(dirtySaves);
        itemNetworks = new ItemNetworkService(database, machines, machineDefinitions);
        power = new PowerNetworkService(database, machines, machineDefinitions, recipes, storage);
        market = new MarketService(storage, economy, database, itemRegistry);
        contracts = new ContractService(storage, economy, database, boosts);
        maintenance = new MaintenanceService(machines, economy, database);
        research = new ResearchService(database, economy);
        gui = new FactoryGuiService(storage, itemRegistry, machineDefinitions, recipes, islands, research, economy, messages);

        loadDefinitions();
        islands.load();
        machines.load();
        rebuildNetworks();

        restartRuntimeTasks();

        registerCommands();
        registerListeners();
        registerPlaceholders();
        getLogger().info("SatisSkyFactory enabled using " + economy.name() + " economy.");
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.stop();
        }
        if (maintenanceTicker != null) {
            maintenanceTicker.stop();
        }
        if (dirtySaves != null) {
            dirtySaves.stop();
        }
        if (placeholderHook != null) {
            placeholderHook.unregister();
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadPluginConfig() {
        configs.load();
        configureSkyblockHook();
        boosts.configure(configs.main());
        loadDefinitions();
        restartRuntimeTasks();
    }

    private void restartRuntimeTasks() {
        if (ticker != null) {
            ticker.stop();
        }
        if (maintenanceTicker != null) {
            maintenanceTicker.stop();
        }
        if (dirtySaves != null) {
            dirtySaves.stop();
        }
        ticker = new MachineTickService(
                this,
                machines,
                machineDefinitions,
                storage,
                recipes,
                research,
                nodes,
                power,
                boosts,
                islands,
                configInt("settings.max-machines-per-tick", "settings.max-machines-per-cycle", 200),
                configs.main().getInt("settings.max-backfill-cycles", 60),
                configs.main().getBoolean("settings.offline-production.enabled", true),
                Math.max(0L, configs.main().getLong("settings.offline-production.max-hours", 8)) * 60L * 60L * 1000L,
                configs.main().getDouble("settings.offline-production.efficiency", 0.35),
                configInt("resource-nodes.link-radius", "settings.resource-node-link-radius", 3),
                Set.copyOf(configs.main().getStringList("limits.recovery-machine-types")),
                maintenanceDouble("maintenance.limited.efficiency", "maintenance.limited-efficiency", 0.5),
                configs.file("maintenance.yml").getInt("maintenance.limited.max-operating-tier", 2),
                configs.file("maintenance.yml").getDouble("maintenance.locked.recovery-efficiency", 0.30),
                configs.file("maintenance.yml").getInt("maintenance.locked.max-operating-tier", 1),
                configs.file("maintenance.yml").getDouble("maintenance.break-wear", 100.0)
        );
        ticker.start(configLong("settings.tick-period-ticks", "settings.tick-interval", 40));
        maintenanceTicker = new MaintenanceTickService(this, islands, skyblock, maintenance);
        maintenanceTicker.start(configLong("settings.maintenance-check-period-ticks", "settings.maintenance-check-interval", 1200));
        dirtySaves.start(configLong("settings.dirty-save-period-ticks", "settings.dirty-save-interval", 200));
    }

    private void configureSkyblockHook() {
        boolean allowSpawnIsland = configBoolean("superior-skyblock.allow-spawn-island", "settings.allow-spawn-island", false);
        skyblock.configure(
                configBoolean("superior-skyblock.allow-coop-build", "settings.allow-coop-build", false),
                !allowSpawnIsland && configBoolean("superior-skyblock.protect-spawn-island", "settings.protect-spawn-island", true)
        );
    }

    private int configInt(String primaryPath, String aliasPath, int fallback) {
        return configs.main().contains(primaryPath)
                ? configs.main().getInt(primaryPath, fallback)
                : configs.main().getInt(aliasPath, fallback);
    }

    private long configLong(String primaryPath, String aliasPath, long fallback) {
        return configs.main().contains(primaryPath)
                ? configs.main().getLong(primaryPath, fallback)
                : configs.main().getLong(aliasPath, fallback);
    }

    private boolean configBoolean(String primaryPath, String aliasPath, boolean fallback) {
        return configs.main().contains(primaryPath)
                ? configs.main().getBoolean(primaryPath, fallback)
                : configs.main().getBoolean(aliasPath, fallback);
    }

    private double maintenanceDouble(String primaryPath, String aliasPath, double fallback) {
        return configs.file("maintenance.yml").contains(primaryPath)
                ? configs.file("maintenance.yml").getDouble(primaryPath, fallback)
                : configs.file("maintenance.yml").getDouble(aliasPath, fallback);
    }

    private void loadDefinitions() {
        itemRegistry.load(configs.file("items.yml"));
        machineDefinitions.load(configs.file("machines.yml"));
        recipes.load(configs.file("recipes.yml"));
        nodes.load(configs.file("resource-nodes.yml"));
        market.load(configs.file("market.yml"));
        contracts.load(configs.file("contracts.yml"));
        maintenance.load(configs.file("maintenance.yml"));
        research.load(configs.file("research.yml"));
    }

    private void registerCommands() {
        FactoryCommand command = new FactoryCommand(
                islands,
                machines,
                machineDefinitions,
                storage,
                nodes,
                skyblock,
                market,
                contracts,
                maintenance,
                research,
                boosts,
                power,
                gui,
                itemFactory,
                itemRegistry,
                messages,
                this::reloadPluginConfig
        );
        PluginCommand factory = getCommand("factory");
        if (factory != null) {
            factory.setExecutor(command);
            factory.setTabCompleter(command);
        }
        PluginCommand sfactory = getCommand("sfactory");
        if (sfactory != null) {
            sfactory.setExecutor(command);
            sfactory.setTabCompleter(command);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MachineListener(
                this,
                itemFactory,
                machineDefinitions,
                machines,
                skyblock,
                islands,
                gui,
                messages,
                research,
                nodes,
                itemNetworks,
                power,
                configs.main(),
                configs.file("maintenance.yml"),
                boosts
        ), this);
        getServer().getPluginManager().registerEvents(new FactoryGuiListener(
                islands,
                skyblock,
                contracts,
                research,
                gui,
                machines,
                recipes,
                storage,
                itemRegistry,
                itemFactory,
                market,
                machineDefinitions,
                maintenance,
                messages
        ), this);
        getServer().getPluginManager().registerEvents(new FactoryLifecycleListener(
                islands,
                skyblock,
                nodes,
                machines,
                itemNetworks,
                power,
                maintenance
        ), this);
    }

    private void rebuildNetworks() {
        machines.all().stream()
                .map(machine -> machine.islandUuid())
                .distinct()
                .forEach(islandUuid -> {
                    itemNetworks.rebuildIsland(islandUuid);
                    power.rebuildIsland(islandUuid);
                });
    }

    private void registerPlaceholders() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderHook = new PlaceholderHook(this, islands, machines, storage, power, boosts, research, contracts);
        placeholderHook.register();
        getLogger().info("Registered PlaceholderAPI expansion: satisskyfactory");
    }
}
