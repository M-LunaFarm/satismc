package kr.seungmin.satisskyfactory.command;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemDefinition;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class FactoryCommand implements CommandExecutor, TabCompleter {
    private final FactoryIslandService islands;
    private final MachineService machines;
    private final StorageService storage;
    private final ResourceNodeService nodes;
    private final SuperiorSkyblockHook skyblock;
    private final MarketService market;
    private final ContractService contracts;
    private final MaintenanceService maintenance;
    private final ResearchService research;
    private final IslandBoostService boosts;
    private final PowerNetworkService power;
    private final FactoryGuiService gui;
    private final CustomItemFactory itemFactory;
    private final ItemRegistry items;
    private final MessageService messages;
    private final AdminFactoryCommand adminCommand;

    public FactoryCommand(FactoryIslandService islands, MachineService machines, MachineDefinitionService definitions,
                          StorageService storage, ResourceNodeService nodes, SuperiorSkyblockHook skyblock,
                          MarketService market, ContractService contracts,
                          MaintenanceService maintenance, ResearchService research, IslandBoostService boosts,
                          PowerNetworkService power, FactoryGuiService gui, CustomItemFactory itemFactory,
                          ItemRegistry items, MessageService messages, Runnable reload) {
        this.islands = islands;
        this.machines = machines;
        this.storage = storage;
        this.nodes = nodes;
        this.skyblock = skyblock;
        this.market = market;
        this.contracts = contracts;
        this.maintenance = maintenance;
        this.research = research;
        this.boosts = boosts;
        this.power = power;
        this.gui = gui;
        this.itemFactory = itemFactory;
        this.items = items;
        this.messages = messages;
        this.adminCommand = new AdminFactoryCommand(islands, machines, definitions, storage, nodes, skyblock,
                maintenance, research, power, itemFactory, items, messages, reload);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.execute(sender, args);
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return true;
        }
        Optional<FactoryContext> context = islands.context(player);
        if (context.isEmpty()) {
            messages.send(player, "no-island");
            return true;
        }
        String sub = args.length == 0 ? "main" : args[0].toLowerCase(Locale.ROOT);
        FactoryContext factoryContext = context.get();
        FactoryIsland island = factoryContext.factoryIsland();
        ensureResourceNodes(player, factoryContext);
        maintenance.chargeIfDue(island, player, factoryContext.islandRef().raw());
        islands.save(island);
        switch (sub) {
            case "help" -> help(player);
            case "main" -> gui.openMain(player, island, machines.byIsland(island.islandUuid()).size(), power.state(island.islandUuid()), boosts.boosts(island.islandUuid()));
            case "status" -> status(player, island);
            case "machines" -> messages.send(player, "machines-count",
                    Map.of("count", String.valueOf(machines.byIsland(island.islandUuid()).size())));
            case "storage" -> gui.openStorage(player, island);
            case "deposit" -> depositHand(player, island);
            case "withdraw" -> withdraw(player, island, args);
            case "market" -> gui.openMarket(player, island, market);
            case "contracts" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("complete")) {
                    contracts.completeAny(island, player).ifPresentOrElse(active -> {
                        refreshMaintenanceStatus(island);
                        messages.send(player, "contract-completed", Map.of("contract", active.template().id()));
                    }, () -> messages.send(player, "contract-requirements-missing"));
                } else {
                    gui.openContracts(player, island, contracts);
                }
            }
            case "research" -> research(player, island, args);
            case "emergency" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("complete")) {
                    if (contracts.completeEmergency(island, player)) {
                        maintenance.updateStatus(island);
                        islands.save(island);
                        messages.send(player, "emergency-contract-completed");
                    } else {
                        messages.send(player, "emergency-contract-unavailable");
                    }
                } else {
                    showEmergency(player, island);
                }
            }
            case "node" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("scan")) {
                    nodes.generateIfMissing(island.islandUuid(), player.getLocation(), location -> isInsideIsland(location, island))
                            .forEach(node -> messages.send(player, "node-scan-result",
                                    Map.of("item", node.resourceId(), "location", node.location().databaseKey())));
                }
            }
            case "sell" -> sell(player, island, args);
            case "repair" -> repairTarget(player, island);
            default -> help(player);
        }
        return true;
    }

    private boolean isInsideIsland(Location location, FactoryIsland island) {
        return skyblock.getIslandAt(location)
                .map(ref -> ref.islandUuid().equals(island.islandUuid()))
                .orElse(false);
    }

    private void ensureResourceNodes(Player player, FactoryContext context) {
        FactoryIsland island = context.factoryIsland();
        Location origin = skyblock.getIslandCenter(context.islandRef()).orElse(player.getLocation());
        nodes.generateIfMissing(island.islandUuid(), origin, location -> isInsideIsland(location, island));
    }

    private void sell(Player player, FactoryIsland island, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("hand")) {
            sellHand(player, island);
            return;
        }
        if (args.length < 3) {
            messages.send(player, "sell-usage");
            return;
        }
        String itemId = args[1];
        long amount = parseLong(args, 2, 0);
        market.sell(island, player, itemId, amount)
                .ifPresentOrElse(result -> {
                            messages.send(player, "sold", Map.of("item", itemId, "amount", String.valueOf(amount), "money", String.valueOf(result.paidToPlayer())));
                            if (result.debtRepaid() > 0) {
                                refreshMaintenanceStatus(island);
                                messages.send(player, "debt-repaid", Map.of("amount", String.valueOf(result.debtRepaid())));
                            }
                        },
                        () -> messages.send(player, "cannot-sell"));
    }

    private void sellHand(Player player, FactoryIsland island) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            messages.send(player, "hold-item-first");
            return;
        }
        Optional<String> resolvedItemId = itemIdForHand(hand);
        if (resolvedItemId.isEmpty()) {
            messages.send(player, "unknown-item");
            return;
        }
        String itemId = resolvedItemId.get();
        int amount = hand.getAmount();
        market.sellDirect(island, player, itemId, amount).ifPresentOrElse(result -> {
            hand.setAmount(0);
            messages.send(player, "sold", Map.of("item", itemId, "amount", String.valueOf(amount), "money", String.valueOf(result.paidToPlayer())));
            if (result.debtRepaid() > 0) {
                refreshMaintenanceStatus(island);
                messages.send(player, "debt-repaid", Map.of("amount", String.valueOf(result.debtRepaid())));
            }
        }, () -> messages.send(player, "hand-item-cannot-sell"));
    }

    private void refreshMaintenanceStatus(FactoryIsland island) {
        maintenance.updateStatus(island);
        islands.save(island);
    }

    private void depositHand(Player player, FactoryIsland island) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            messages.send(player, "hold-item-first");
            return;
        }
        Optional<String> resolvedItemId = itemIdForHand(hand);
        if (resolvedItemId.isEmpty()) {
            messages.send(player, "unknown-item");
            return;
        }
        String itemId = resolvedItemId.get();
        long amount = hand.getAmount();
        var inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.add(itemId, amount)) {
            messages.send(player, "storage-full");
            return;
        }
        hand.setAmount(0);
        storage.save(inventory);
        messages.send(player, "deposited", Map.of("item", itemId, "amount", String.valueOf(amount)));
    }

    private Optional<String> itemIdForHand(ItemStack stack) {
        if (itemFactory.isMachineItem(stack)) {
            return Optional.empty();
        }
        Optional<String> pdcItemId = itemFactory.factoryItemId(stack);
        if (pdcItemId.isPresent()) {
            return pdcItemId;
        }
        return Optional.of(items.itemIdForMaterial(stack.getType())
                .orElseGet(() -> stack.getType().name().toLowerCase(Locale.ROOT)));
    }

    private void withdraw(Player player, FactoryIsland island, String[] args) {
        if (args.length < 3) {
            messages.send(player, "withdraw-usage");
            return;
        }
        String itemId = args[1];
        long amount = parseLong(args, 2, 0);
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            messages.send(player, "invalid-amount");
            return;
        }
        if (items.get(itemId).map(ItemDefinition::virtualOnly).orElse(false)) {
            messages.send(player, "virtual-only-withdraw");
            return;
        }
        var inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.remove(itemId, amount)) {
            messages.send(player, "not-enough-storage");
            return;
        }
        long returned = giveVirtualItem(player, itemId, amount);
        if (returned > 0) {
            inventory.add(itemId, returned);
            messages.send(player, "inventory-full");
        }
        storage.save(inventory);
        messages.send(player, "withdrew", Map.of("item", itemId, "amount", String.valueOf(amount - returned)));
    }

    private long giveVirtualItem(Player player, String itemId, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            ItemStack stack = itemStack(itemId, remaining);
            int stackAmount = stack.getAmount();
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                return overflow.values().stream().mapToLong(ItemStack::getAmount).sum()
                        + Math.max(0, remaining - stackAmount);
            }
            remaining -= stackAmount;
        }
        return 0;
    }

    private ItemStack itemStack(String itemId, long amount) {
        return items.get(itemId)
                .map(item -> itemFactory.factoryItem(item, stackAmount(item.material(), amount)))
                .orElseGet(() -> {
                    Material material = material(itemId);
                    return new ItemStack(material, stackAmount(material, amount));
                });
    }

    private int stackAmount(Material material, long amount) {
        int maxStackSize = Math.max(1, material.getMaxStackSize());
        return (int) Math.max(1, Math.min(maxStackSize, amount));
    }

    private Material material(String itemId) {
        Material material = Material.matchMaterial(itemId.toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }

    private void status(Player player, FactoryIsland island) {
        messages.send(player, "status-tier", Map.of("tier", String.valueOf(island.tier())));
        messages.send(player, "status-research", Map.of("research", String.valueOf(island.researchPoints())));
        messages.send(player, "status-debt", Map.of("debt", String.valueOf(island.maintenanceDebt()), "status", island.maintenanceStatus().name()));
        messages.send(player, "status-machines", Map.of("count", String.valueOf(machines.byIsland(island.islandUuid()).size())));
        messages.send(player, "status-storage", Map.of("used", String.valueOf(storage.islandStorage(island.islandUuid()).used())));
        var boost = boosts.boosts(island.islandUuid());
        messages.send(player, "status-boosts", Map.of(
                "agriculture", String.format(Locale.US, "%.2f", boost.agricultureBoost()),
                "machine", String.valueOf(boost.factorySlotBonus()),
                "contract", String.valueOf(boost.contractSlotBonus())));
    }

    private void research(Player player, FactoryIsland island, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("unlock")) {
            ResearchService.UnlockResult result = research.unlock(island, player, args[2]);
            islands.save(island);
            messages.send(player, "research-unlock-result", Map.of("result", result.name()));
            return;
        }
        gui.openResearch(player, island, research);
    }

    private void showEmergency(Player player, FactoryIsland island) {
        contracts.emergencyTemplate().ifPresentOrElse(template -> {
            messages.send(player, "emergency-contract", Map.of("contract", template.id()));
            messages.send(player, "status-debt", Map.of("debt", String.valueOf(island.maintenanceDebt()), "status", island.maintenanceStatus().name()));
            messages.send(player, "emergency-required", Map.of("required", template.required().toString()));
            messages.send(player, "emergency-rewards", Map.of(
                    "money", String.valueOf(template.money()),
                    "research", String.valueOf(template.research()),
                    "reputation", String.valueOf(template.reputation()),
                    "debt", String.valueOf(template.debtRelief()),
                    "items", template.itemRewards().toString()));
            messages.send(player, "emergency-used", Map.of("used", String.valueOf(contracts.emergencyUsedToday(island)),
                    "limit", String.valueOf(contracts.emergencyDailyLimit())));
            messages.send(player, "emergency-complete-help");
        }, () -> messages.send(player, "no-emergency-contract"));
    }

    private void help(Player player) {
        messages.send(player, "help-main");
        messages.send(player, "help-actions");
    }

    private void repairTarget(Player player, FactoryIsland island) {
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(player, "no-target-machine");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            if (!machine.islandUuid().equals(island.islandUuid())) {
                messages.send(player, "machine-wrong-island");
                return;
            }
            if (!consumeRepairParts(island, machine)) {
                messages.send(player, "repair-requires", Map.of("cost", repairCostText(machine)));
                return;
            }
            repair(machine);
            messages.send(player, "machine-repaired");
        }, () -> messages.send(player, "no-machine-here"));
    }

    private boolean consumeRepairParts(FactoryIsland island, MachineInstance machine) {
        var inventory = storage.islandStorage(island.islandUuid());
        Map<String, Long> cost = maintenance.repairCost(machine.status() == MachineStatus.BROKEN);
        if (cost.entrySet().stream().anyMatch(entry -> inventory.amount(entry.getKey()) < entry.getValue())) {
            return false;
        }
        cost.forEach(inventory::remove);
        storage.save(inventory);
        return true;
    }

    private String repairCostText(MachineInstance machine) {
        Map<String, Long> cost = maintenance.repairCost(machine.status() == MachineStatus.BROKEN);
        if (cost.isEmpty()) {
            return messages.raw("no-materials");
        }
        return cost.entrySet().stream()
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse(messages.raw("no-materials"));
    }

    private void repair(MachineInstance machine) {
        machine.wear(0.0);
        machine.status(MachineStatus.SLEEPING);
        machines.save(machine);
    }

    private long parseLong(String[] args, int index, long fallback) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("help", "main", "status", "machines", "storage", "deposit", "withdraw", "contracts", "market", "research", "emergency", "node", "sell", "repair", "admin"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            List<String> values = new ArrayList<>();
            values.add("hand");
            values.addAll(market.prices().keySet().stream().sorted().toList());
            return filter(values, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("sell") && !args[1].equalsIgnoreCase("hand")) {
            return filter(amountSuggestions(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("withdraw")) {
            return filter(storedItemIds(sender), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("withdraw")) {
            return filter(amountSuggestions(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("contracts")) {
            return filter(List.of("complete"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("emergency")) {
            return filter(List.of("complete"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("node")) {
            return filter(List.of("scan"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("research")) {
            return filter(List.of("unlock"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.complete(sender, args);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("research") && args[1].equalsIgnoreCase("unlock")) {
            return filter(research.all().keySet().stream().toList(), args[2]);
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.complete(sender, args);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private List<String> storedItemIds(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return itemIds();
        }
        return islands.context(player)
                .map(context -> storage.islandStorage(context.factoryIsland().islandUuid()).items().keySet().stream()
                        .sorted()
                        .toList())
                .filter(values -> !values.isEmpty())
                .orElseGet(this::itemIds);
    }

    private List<String> itemIds() {
        return items.all().keySet().stream().sorted().toList();
    }

    private List<String> amountSuggestions() {
        return List.of("1", "8", "16", "32", "64", "256", "1024");
    }

}
