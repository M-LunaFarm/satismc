package kr.seungmin.satisskyfactory.command;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import org.bukkit.Bukkit;
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
    private final MachineDefinitionService definitions;
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
    private final Runnable reload;

    public FactoryCommand(FactoryIslandService islands, MachineService machines, MachineDefinitionService definitions,
                          StorageService storage, ResourceNodeService nodes, SuperiorSkyblockHook skyblock,
                          MarketService market, ContractService contracts,
                          MaintenanceService maintenance, ResearchService research, IslandBoostService boosts,
                          PowerNetworkService power, FactoryGuiService gui, CustomItemFactory itemFactory,
                          ItemRegistry items, MessageService messages, Runnable reload) {
        this.islands = islands;
        this.machines = machines;
        this.definitions = definitions;
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
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return admin(sender, args);
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

    private boolean admin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!sender.hasPermission("satisskyfactory.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
            messages.send(sender, "admin-usage");
            return true;
        }
        String subcommand = args[1].toLowerCase(Locale.ROOT);
        if (subcommand.equals("debug")) {
            if (!sender.hasPermission("satisskyfactory.debug") && !sender.hasPermission("satisskyfactory.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
        } else if (!sender.hasPermission("satisskyfactory.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        switch (subcommand) {
            case "reload" -> {
                reload.run();
                messages.send(sender, "reloaded");
            }
            case "give" -> giveMachine(sender, args);
            case "giveitem" -> giveItem(sender, args);
            case "addresearch" -> withPlayerContext(sender, args, 2, (target, island) -> {
                research.addResearch(island, parseLong(args, 3, 0));
                islands.save(island);
                messages.send(sender, "admin-research-updated");
            });
            case "setdebt" -> withPlayerContext(sender, args, 2, (target, island) -> {
                maintenance.setDebt(island, parseLong(args, 3, 0));
                islands.save(island);
                messages.send(sender, "admin-debt-updated");
            });
            case "charge" -> withPlayerContext(sender, args, 2, (target, island) -> {
                islands.context(target).ifPresent(context -> maintenance.chargeIfDue(island, target, context.islandRef().raw()));
                islands.save(island);
                messages.send(sender, "admin-maintenance-charged");
            });
            case "gennodes" -> withPlayerContext(sender, args, 2, (target, island) -> {
                nodes.generateIfMissing(island.islandUuid(), target.getLocation(), location -> isInsideIsland(location, island));
                messages.send(sender, "admin-nodes-generated");
            });
            case "debug" -> debug(sender, args);
            case "removehere" -> removeHere(sender);
            case "repairhere" -> repairHere(sender);
            default -> messages.send(sender, "unknown-admin-command");
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
        if (items.get(itemId).map(ItemRegistry.FactoryItem::virtualOnly).orElse(false)) {
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

    private void giveMachine(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "admin-give-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        definitions.get(args[3]).ifPresentOrElse(definition -> {
            long amount = parseLong(args, 4, 1);
            if (amount <= 0) {
                messages.send(sender, "invalid-amount");
                return;
            }
            long returned = giveMachineItem(target, definition, amount);
            if (returned > 0) {
                messages.send(sender, "target-inventory-full", Map.of("amount", String.valueOf(returned)));
            }
            messages.send(sender, "given");
        }, () -> messages.send(sender, "unknown-machine"));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messages.send(sender, "admin-giveitem-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        items.get(args[3]).ifPresentOrElse(item -> {
            long amount = parseLong(args, 4, 0);
            if (amount <= 0) {
                messages.send(sender, "invalid-amount");
                return;
            }
            if (item.virtualOnly()) {
                if (!giveVirtualOnlyItem(sender, target, item.id(), amount)) {
                    return;
                }
            } else {
                long returned = giveVirtualItem(target, item.id(), amount);
                if (returned > 0) {
                    messages.send(sender, "target-inventory-full", Map.of("amount", String.valueOf(returned)));
                }
            }
            messages.send(sender, "given");
        }, () -> messages.send(sender, "unknown-item"));
    }

    private long giveMachineItem(Player target, MachineDefinition definition, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            ItemStack stack = itemFactory.machineItem(definition, stackAmount(definition.material(), remaining));
            int stackAmount = stack.getAmount();
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                return overflow.values().stream().mapToLong(ItemStack::getAmount).sum()
                        + Math.max(0, remaining - stackAmount);
            }
            remaining -= stackAmount;
        }
        return 0;
    }

    private boolean giveVirtualOnlyItem(CommandSender sender, Player target, String itemId, long amount) {
        return islands.context(target).map(context -> {
            var inventory = storage.islandStorage(context.factoryIsland().islandUuid());
            if (!inventory.add(itemId, amount)) {
                messages.send(sender, "storage-full");
                return false;
            }
            storage.save(inventory);
            return true;
        }).orElseGet(() -> {
            messages.send(sender, "no-island");
            return false;
        });
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length < 3 || !(sender instanceof Player player)) {
            return;
        }
        islands.context(player).ifPresent(context -> {
            if (args[2].equalsIgnoreCase("island")) {
                messages.send(sender, "debug-island", Map.of("island", context.factoryIsland().islandUuid().toString()));
            } else if (args[2].equalsIgnoreCase("networks")) {
                var state = power.state(context.factoryIsland().islandUuid());
                messages.send(sender, "debug-networks", Map.of(
                        "machines", String.valueOf(machines.byIsland(context.factoryIsland().islandUuid()).size()),
                        "ratio", String.format(Locale.US, "%.2f", state.ratio()),
                        "generation", String.format(Locale.US, "%.1f", state.generation()),
                        "consumption", String.format(Locale.US, "%.1f", state.consumption()),
                        "battery", state.batteryStored() + "/" + String.format(Locale.US, "%.0f", state.batteryCapacity())));
            }
        });
    }

    private void removeHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(sender, "no-target-block");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            machines.forceRemove(machine);
            block.setType(Material.AIR, false);
            messages.send(sender, "machine-removed-admin");
        }, () -> messages.send(sender, "no-machine-here"));
    }

    private void repairHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(sender, "no-target-block");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            repair(machine);
            messages.send(sender, "machine-repaired");
        }, () -> messages.send(sender, "no-machine-here"));
    }

    private void repair(MachineInstance machine) {
        machine.wear(0.0);
        machine.status(MachineStatus.SLEEPING);
        machines.save(machine);
    }

    private void withPlayerContext(CommandSender sender, String[] args, int playerIndex, AdminContextConsumer consumer) {
        if (args.length <= playerIndex) {
            messages.send(sender, "player-required");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[playerIndex]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        islands.context(target).ifPresentOrElse(context -> consumer.accept(target, context.factoryIsland()), () -> messages.send(sender, "no-island"));
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
            return filter(List.of("reload", "give", "giveitem", "addresearch", "setdebt", "charge", "gennodes", "debug", "removehere", "repairhere"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("research") && args[1].equalsIgnoreCase("unlock")) {
            return filter(research.all().keySet().stream().toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && needsPlayer(args[1])) {
            return filter(onlinePlayerNames(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("give")) {
            return filter(definitions.all().stream().map(machine -> machine.typeId()).toList(), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("giveitem")) {
            return filter(itemIds(), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("giveitem")
                || args[1].equalsIgnoreCase("addresearch") || args[1].equalsIgnoreCase("setdebt"))) {
            return filter(amountSuggestions(), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("giveitem"))) {
            return filter(amountSuggestions(), args[4]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("debug")) {
            return filter(List.of("island", "networks"), args[2]);
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

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> amountSuggestions() {
        return List.of("1", "8", "16", "32", "64", "256", "1024");
    }

    private boolean needsPlayer(String adminSubcommand) {
        return adminSubcommand.equalsIgnoreCase("give")
                || adminSubcommand.equalsIgnoreCase("giveitem")
                || adminSubcommand.equalsIgnoreCase("addresearch")
                || adminSubcommand.equalsIgnoreCase("setdebt")
                || adminSubcommand.equalsIgnoreCase("charge")
                || adminSubcommand.equalsIgnoreCase("gennodes");
    }

    @FunctionalInterface
    private interface AdminContextConsumer {
        void accept(Player player, FactoryIsland island);
    }
}
