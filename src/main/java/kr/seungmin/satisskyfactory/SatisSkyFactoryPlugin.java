package kr.seungmin.satisskyfactory;

import kr.seungmin.satisskyfactory.command.FactoryCommand;
import kr.seungmin.satisskyfactory.config.ConfigService;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.economy.VaultEconomyService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.listener.FactoryGuiListener;
import kr.seungmin.satisskyfactory.listener.MachineListener;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.task.MachineTickService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

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
    private ResourceNodeService nodes;
    private PowerNetworkService power;
    private MarketService market;
    private ContractService contracts;
    private MaintenanceService maintenance;
    private FactoryGuiService gui;
    private MachineTickService ticker;

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

        database = new DatabaseService(this);
        database.open();

        economy = VaultEconomyService.createOrFallback(this);
        itemRegistry = new ItemRegistry();
        itemFactory = new CustomItemFactory(this);
        machineDefinitions = new MachineDefinitionService();
        recipes = new RecipeService();
        storage = new StorageService(database, configs.main().getInt("storage.default-capacity", 10000));
        islands = new FactoryIslandService(skyblock, database);
        machines = new MachineService(database, machineDefinitions, storage);
        nodes = new ResourceNodeService(database);
        power = new PowerNetworkService(machines, machineDefinitions, storage);
        market = new MarketService(storage, economy, database);
        contracts = new ContractService(storage, economy, database);
        maintenance = new MaintenanceService(machines, economy, database);
        gui = new FactoryGuiService(storage, itemRegistry, machineDefinitions);

        loadDefinitions();
        machines.load();

        ticker = new MachineTickService(
                this,
                machines,
                machineDefinitions,
                storage,
                recipes,
                nodes,
                power,
                configs.main().getInt("settings.max-machines-per-cycle", 200)
        );
        ticker.start(configs.main().getLong("settings.tick-interval", 40));

        registerCommands();
        registerListeners();
        getLogger().info("SatisSkyFactory enabled using " + economy.name() + " economy.");
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.stop();
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadPluginConfig() {
        configs.load();
        loadDefinitions();
    }

    private void loadDefinitions() {
        itemRegistry.load(configs.file("items.yml"));
        machineDefinitions.load(configs.file("machines.yml"));
        recipes.load(configs.file("recipes.yml"));
        nodes.load(configs.file("resource-nodes.yml"));
        market.load(configs.file("market.yml"));
        contracts.load(configs.file("contracts.yml"));
        maintenance.load(configs.file("maintenance.yml"));
    }

    private void registerCommands() {
        FactoryCommand command = new FactoryCommand(
                islands,
                machines,
                machineDefinitions,
                storage,
                nodes,
                market,
                contracts,
                maintenance,
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
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MachineListener(
                itemFactory,
                machineDefinitions,
                machines,
                skyblock,
                islands,
                gui,
                messages,
                configs.main()
        ), this);
        getServer().getPluginManager().registerEvents(new FactoryGuiListener(), this);
    }
}
