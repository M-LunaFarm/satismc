package kr.seungmin.satisskyfactory.command;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import org.bukkit.Bukkit;
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
    private final MarketService market;
    private final ContractService contracts;
    private final MaintenanceService maintenance;
    private final FactoryGuiService gui;
    private final CustomItemFactory itemFactory;
    private final ItemRegistry items;
    private final MessageService messages;
    private final Runnable reload;

    public FactoryCommand(FactoryIslandService islands, MachineService machines, MachineDefinitionService definitions,
                          StorageService storage, ResourceNodeService nodes, MarketService market, ContractService contracts,
                          MaintenanceService maintenance, FactoryGuiService gui, CustomItemFactory itemFactory,
                          ItemRegistry items, MessageService messages, Runnable reload) {
        this.islands = islands;
        this.machines = machines;
        this.definitions = definitions;
        this.storage = storage;
        this.nodes = nodes;
        this.market = market;
        this.contracts = contracts;
        this.maintenance = maintenance;
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
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        FactoryIsland island = context.get().factoryIsland();
        switch (sub) {
            case "help" -> help(player);
            case "status" -> status(player, island);
            case "machines" -> player.sendMessage("Machines: " + machines.byIsland(island.islandUuid()).size());
            case "storage" -> gui.openStorage(player, island);
            case "market" -> market.prices().forEach((item, price) -> player.sendMessage(item + ": " + price));
            case "contracts" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("complete")) {
                    contracts.completeAny(island, player).ifPresentOrElse(template -> {
                        islands.save(island);
                        player.sendMessage("Contract completed: " + template.id());
                    }, () -> player.sendMessage("No contract requirements are ready."));
                } else {
                    contracts.templates().values().forEach(template -> player.sendMessage(template.id() + " requires " + template.required()));
                }
            }
            case "research" -> player.sendMessage("Research points: " + island.researchPoints());
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
                    nodes.generateIfMissing(island.islandUuid(), player.getLocation())
                            .forEach(node -> player.sendMessage(node.resourceId() + " node at " + node.location().databaseKey()));
                }
            }
            case "sell" -> sell(player, island, args);
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
            sender.sendMessage("/factory admin reload|give|giveitem|addresearch|setdebt|charge|gennodes|debug|removehere");
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
                island.researchPoints(island.researchPoints() + parseLong(args, 3, 0));
                islands.save(island);
                sender.sendMessage("Research updated.");
            });
            case "setdebt" -> withPlayerContext(sender, args, 2, (target, island) -> {
                maintenance.setDebt(island, parseLong(args, 3, 0));
                islands.save(island);
                sender.sendMessage("Debt updated.");
            });
            case "charge" -> withPlayerContext(sender, args, 2, (target, island) -> {
                maintenance.chargeIfDue(island, target);
                islands.save(island);
                sender.sendMessage("Maintenance charged if due.");
            });
            case "gennodes" -> withPlayerContext(sender, args, 2, (target, island) -> {
                nodes.generateIfMissing(island.islandUuid(), target.getLocation());
                sender.sendMessage("Resource nodes generated.");
            });
            case "debug" -> debug(sender, args);
            case "removehere" -> removeHere(sender);
            default -> sender.sendMessage("Unknown admin command.");
        }
        return true;
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
        market.sell(island.islandUuid(), player, itemId, amount)
                .ifPresentOrElse(money -> messages.send(player, "sold", Map.of("item", itemId, "amount", String.valueOf(amount), "money", String.valueOf(money))),
                        () -> player.sendMessage("Cannot sell that item or amount."));
    }

    private void sellHand(Player player, FactoryIsland island) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String itemId = itemFactory.factoryItemId(hand).orElseGet(() -> hand.getType().name().toLowerCase(Locale.ROOT));
        int amount = hand.getAmount();
        market.sellDirect(island.islandUuid(), player, itemId, amount).ifPresentOrElse(money -> {
            hand.setAmount(0);
            messages.send(player, "sold", Map.of("item", itemId, "amount", String.valueOf(amount), "money", String.valueOf(money)));
        }, () -> player.sendMessage("The item in your hand cannot be sold."));
    }

    private void status(Player player, FactoryIsland island) {
        player.sendMessage("Tier: " + island.tier());
        player.sendMessage("Research: " + island.researchPoints());
        player.sendMessage("Debt: " + island.maintenanceDebt() + " (" + island.maintenanceStatus() + ")");
        player.sendMessage("Machines: " + machines.byIsland(island.islandUuid()).size());
        player.sendMessage("Storage used: " + storage.islandStorage(island.islandUuid()).used());
    }

    private void help(Player player) {
        player.sendMessage("/factory status, storage, machines, market, contracts, research");
        player.sendMessage("/factory node scan, sell <itemId> <amount>, emergency");
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
                sender.sendMessage("Island-wide MVP network, machines=" + machines.byIsland(context.factoryIsland().islandUuid()).size());
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
            machines.remove(machine);
            sender.sendMessage("Machine removed.");
        }, () -> sender.sendMessage("No machine here."));
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
            return filter(List.of("help", "status", "machines", "storage", "contracts", "market", "research", "emergency", "node", "sell", "admin"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(List.of("reload", "give", "giveitem", "addresearch", "setdebt", "charge", "gennodes", "debug", "removehere"), args[1]);
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
