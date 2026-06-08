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
        FactoryIsland island = context.get().factoryIsland();
        maintenance.chargeIfDue(island, player, context.get().islandRef().raw());
        islands.save(island);
        switch (sub) {
            case "help" -> help(player);
            case "main" -> gui.openMain(player, island, machines.byIsland(island.islandUuid()).size(), power.state(island.islandUuid()), boosts.boosts(island.islandUuid()));
            case "status" -> status(player, island);
            case "machines" -> player.sendMessage("Machines: " + machines.byIsland(island.islandUuid()).size());
            case "storage" -> gui.openStorage(player, island);
            case "deposit" -> depositHand(player, island);
            case "withdraw" -> withdraw(player, island, args);
            case "market" -> gui.openMarket(player, island, market);
            case "contracts" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("complete")) {
                    contracts.completeAny(island, player).ifPresentOrElse(active -> {
                        islands.save(island);
                        player.sendMessage("Contract completed: " + active.template().id());
                    }, () -> player.sendMessage("No contract requirements are ready."));
                } else {
                    gui.openContracts(player, island, contracts);
                }
            }
            case "research" -> research(player, island, args);
            case "emergency" -> {
                if (contracts.completeEmergency(island, player)) {
                    maintenance.updateStatus(island);
                    islands.save(island);
                    player.sendMessage("Emergency contract completed.");
                } else {
                    player.sendMessage("Emergency contract requirements are missing.");
                }
            }
            case "node" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("scan")) {
                    nodes.generateIfMissing(island.islandUuid(), player.getLocation(), location -> isInsideIsland(location, island))
                            .forEach(node -> player.sendMessage(node.resourceId() + " node at " + node.location().databaseKey()));
                }
            }
            case "sell" -> sell(player, island, args);
            case "repair" -> repairTarget(player, island);
            default -> help(player);
        }
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("satisskyfactory.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("/factory admin reload|give|giveitem|addresearch|setdebt|charge|gennodes|debug|removehere|repairhere");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reload.run();
                messages.send(sender, "reloaded");
            }
            case "give" -> giveMachine(sender, args);
            case "giveitem" -> giveItem(sender, args);
            case "addresearch" -> withPlayerContext(sender, args, 2, (target, island) -> {
                research.addResearch(island, parseLong(args, 3, 0));
                islands.save(island);
                sender.sendMessage("Research updated.");
            });
            case "setdebt" -> withPlayerContext(sender, args, 2, (target, island) -> {
                maintenance.setDebt(island, parseLong(args, 3, 0));
                islands.save(island);
                sender.sendMessage("Debt updated.");
            });
            case "charge" -> withPlayerContext(sender, args, 2, (target, island) -> {
                islands.context(target).ifPresent(context -> maintenance.chargeIfDue(island, target, context.islandRef().raw()));
                islands.save(island);
                sender.sendMessage("Maintenance charged if due.");
            });
            case "gennodes" -> withPlayerContext(sender, args, 2, (target, island) -> {
                nodes.generateIfMissing(island.islandUuid(), target.getLocation(), location -> isInsideIsland(location, island));
                sender.sendMessage("Resource nodes generated.");
            });
            case "debug" -> debug(sender, args);
            case "removehere" -> removeHere(sender);
            case "repairhere" -> repairHere(sender);
            default -> sender.sendMessage("Unknown admin command.");
        }
        return true;
    }

    private boolean isInsideIsland(Location location, FactoryIsland island) {
        return skyblock.getIslandAt(location)
                .map(ref -> ref.islandUuid().equals(island.islandUuid()))
                .orElse(false);
    }

    private void sell(Player player, FactoryIsland island, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("hand")) {
            sellHand(player, island);
            return;
        }
        if (args.length < 3) {
            player.sendMessage("/factory sell <itemId> <amount>");
            return;
        }
        String itemId = args[1];
        long amount = parseLong(args, 2, 0);
        market.sell(island, player, itemId, amount)
                .ifPresentOrElse(result -> {
                            messages.send(player, "sold", Map.of("item", itemId, "amount", String.valueOf(amount), "money", String.valueOf(result.paidToPlayer())));
                            if (result.debtRepaid() > 0) {
                                player.sendMessage("Debt repaid from sale: " + result.debtRepaid());
                            }
                        },
                        () -> player.sendMessage("Cannot sell that item or amount."));
    }

    private void sellHand(Player player, FactoryIsland island) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            player.sendMessage("Hold an item first.");
            return;
        }
        String itemId = itemIdForHand(hand);
        int amount = hand.getAmount();
        market.sellDirect(island, player, itemId, amount).ifPresentOrElse(result -> {
            hand.setAmount(0);
            messages.send(player, "sold", Map.of("item", itemId, "amount", String.valueOf(amount), "money", String.valueOf(result.paidToPlayer())));
            if (result.debtRepaid() > 0) {
                player.sendMessage("Debt repaid from sale: " + result.debtRepaid());
            }
        }, () -> player.sendMessage("The item in your hand cannot be sold."));
    }

    private void depositHand(Player player, FactoryIsland island) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            player.sendMessage("Hold an item first.");
            return;
        }
        String itemId = itemIdForHand(hand);
        long amount = hand.getAmount();
        var inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.add(itemId, amount)) {
            player.sendMessage("Factory storage is full.");
            return;
        }
        hand.setAmount(0);
        storage.save(inventory);
        messages.send(player, "deposited", Map.of("item", itemId, "amount", String.valueOf(amount)));
    }

    private String itemIdForHand(ItemStack stack) {
        Optional<String> pdcItemId = itemFactory.factoryItemId(stack);
        if (pdcItemId.isPresent()) {
            return pdcItemId.get();
        }
        return items.itemIdForMaterial(stack.getType())
                .orElseGet(() -> stack.getType().name().toLowerCase(Locale.ROOT));
    }

    private void withdraw(Player player, FactoryIsland island, String[] args) {
        if (args.length < 3) {
            player.sendMessage("/factory withdraw <itemId> <amount>");
            return;
        }
        String itemId = args[1];
        long amount = parseLong(args, 2, 0);
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            player.sendMessage("Invalid amount.");
            return;
        }
        var inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.remove(itemId, amount)) {
            player.sendMessage("Not enough items in storage.");
            return;
        }
        ItemStack stack = items.get(itemId)
                .map(item -> itemFactory.factoryItem(item, (int) amount))
                .orElseGet(() -> new ItemStack(Material.matchMaterial(itemId.toUpperCase(Locale.ROOT)) == null ? Material.PAPER : Material.matchMaterial(itemId.toUpperCase(Locale.ROOT)), (int) amount));
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(leftover -> inventory.add(itemId, leftover.getAmount()));
            player.sendMessage("Your inventory is full.");
        }
        storage.save(inventory);
        messages.send(player, "withdrew", Map.of("item", itemId, "amount", String.valueOf(amount - overflow.values().stream().mapToInt(ItemStack::getAmount).sum())));
    }

    private void status(Player player, FactoryIsland island) {
        player.sendMessage("Tier: " + island.tier());
        player.sendMessage("Research: " + island.researchPoints());
        player.sendMessage("Debt: " + island.maintenanceDebt() + " (" + island.maintenanceStatus() + ")");
        player.sendMessage("Machines: " + machines.byIsland(island.islandUuid()).size());
        player.sendMessage("Storage used: " + storage.islandStorage(island.islandUuid()).used());
        var boost = boosts.boosts(island.islandUuid());
        player.sendMessage("Boosts: agriculture x" + String.format(Locale.US, "%.2f", boost.agricultureBoost())
                + ", machine slots +" + boost.factorySlotBonus()
                + ", contract slots +" + boost.contractSlotBonus());
    }

    private void research(Player player, FactoryIsland island, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("unlock")) {
            ResearchService.UnlockResult result = research.unlock(island, player, args[2]);
            islands.save(island);
            player.sendMessage("Research unlock result: " + result.name());
            return;
        }
        gui.openResearch(player, island, research);
    }

    private void help(Player player) {
        player.sendMessage("/factory status, storage, machines, market, contracts, research, repair");
        player.sendMessage("/factory node scan, sell <itemId> <amount>, emergency");
    }

    private void repairTarget(Player player, FactoryIsland island) {
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            player.sendMessage("No target machine.");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            if (!machine.islandUuid().equals(island.islandUuid())) {
                player.sendMessage("That machine belongs to another island.");
                return;
            }
            if (!consumeRepairParts(island, machine)) {
                player.sendMessage("Repair requires " + repairCostText(machine) + ".");
                return;
            }
            repair(machine);
            player.sendMessage("Machine repaired.");
        }, () -> player.sendMessage("No machine here."));
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
            return "no materials";
        }
        return cost.entrySet().stream()
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("no materials");
    }

    private void giveMachine(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("/factory admin give <player> <machineType> [amount]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return;
        }
        definitions.get(args[3]).ifPresentOrElse(definition -> {
            target.getInventory().addItem(itemFactory.machineItem(definition, (int) parseLong(args, 4, 1)));
            messages.send(sender, "given");
        }, () -> messages.send(sender, "unknown-machine"));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("/factory admin giveitem <player> <itemId> <amount>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return;
        }
        items.get(args[3]).ifPresentOrElse(item -> {
            ItemStack stack = itemFactory.factoryItem(item, (int) parseLong(args, 4, 1));
            target.getInventory().addItem(stack);
            islands.context(target).ifPresent(context -> {
                storage.islandStorage(context.factoryIsland().islandUuid()).add(item.id(), parseLong(args, 4, 1));
                storage.save(storage.islandStorage(context.factoryIsland().islandUuid()));
            });
            messages.send(sender, "given");
        }, () -> messages.send(sender, "unknown-item"));
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length < 3 || !(sender instanceof Player player)) {
            return;
        }
        islands.context(player).ifPresent(context -> {
            if (args[2].equalsIgnoreCase("island")) {
                sender.sendMessage(context.factoryIsland().islandUuid().toString());
            } else if (args[2].equalsIgnoreCase("networks")) {
                var state = power.state(context.factoryIsland().islandUuid());
                sender.sendMessage("Power and adjacency logistics, machines=" + machines.byIsland(context.factoryIsland().islandUuid()).size()
                        + ", ratio=" + String.format(Locale.US, "%.2f", state.ratio())
                        + ", generation=" + String.format(Locale.US, "%.1f", state.generation())
                        + ", consumption=" + String.format(Locale.US, "%.1f", state.consumption())
                        + ", battery=" + state.batteryStored() + "/" + String.format(Locale.US, "%.0f", state.batteryCapacity()));
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
            sender.sendMessage("No target block.");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            if (machines.remove(machine)) {
                block.setType(Material.AIR, false);
                sender.sendMessage("Machine removed.");
            } else {
                sender.sendMessage("Factory storage is full. Empty some space before removing this machine.");
            }
        }, () -> sender.sendMessage("No machine here."));
    }

    private void repairHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            sender.sendMessage("No target block.");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            repair(machine);
            sender.sendMessage("Machine repaired.");
        }, () -> sender.sendMessage("No machine here."));
    }

    private void repair(MachineInstance machine) {
        machine.wear(0.0);
        machine.status(MachineStatus.IDLE);
        machines.save(machine);
    }

    private void withPlayerContext(CommandSender sender, String[] args, int playerIndex, AdminContextConsumer consumer) {
        if (args.length <= playerIndex) {
            sender.sendMessage("Player is required.");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[playerIndex]);
        if (target == null) {
            sender.sendMessage("Player not found.");
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
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(List.of("reload", "give", "giveitem", "addresearch", "setdebt", "charge", "gennodes", "debug", "removehere", "repairhere"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("research") && args[1].equalsIgnoreCase("unlock")) {
            return filter(research.all().keySet().stream().toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("give")) {
            return filter(definitions.all().stream().map(machine -> machine.typeId()).toList(), args[3]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> values, String prefix) {
        return values.stream().filter(value -> value.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }

    @FunctionalInterface
    private interface AdminContextConsumer {
        void accept(Player player, FactoryIsland island);
    }
}
