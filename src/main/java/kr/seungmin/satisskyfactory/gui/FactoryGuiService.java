package kr.seungmin.satisskyfactory.gui;

import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.recipe.RecipeDefinition;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.research.UnlockDefinition;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FactoryGuiService {
    private final StorageService storage;
    private final ItemRegistry items;
    private final MachineDefinitionService definitions;
    private final RecipeService recipes;
    private final FactoryIslandService islands;
    private final ResearchService research;
    private final EconomyService economy;
    private final MessageService messages;

    public FactoryGuiService(StorageService storage, ItemRegistry items, MachineDefinitionService definitions,
                             RecipeService recipes, FactoryIslandService islands, ResearchService research,
                             EconomyService economy, MessageService messages) {
        this.storage = storage;
        this.items = items;
        this.definitions = definitions;
        this.recipes = recipes;
        this.islands = islands;
        this.research = research;
        this.economy = economy;
        this.messages = messages;
    }

    public void openMain(Player player, FactoryIsland island, int machineCount, PowerNetworkService.NetworkState powerState,
                         IslandBoostService.Boosts boosts) {
        FactoryGuiHolder holder = new FactoryGuiHolder("main", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("main-title", "SatisSkyFactory"));
        holder.inventory(inventory);
        inventory.setItem(10, icon(Material.CRAFTING_TABLE, ChatColor.GOLD + "Factory",
                List.of(ChatColor.GRAY + "Tier: " + island.tier(),
                        ChatColor.GRAY + "Machines: " + machineCount,
                        ChatColor.GRAY + "Storage used: " + storage.islandStorage(island.islandUuid()).used())));
        inventory.setItem(12, icon(Material.REDSTONE, ChatColor.RED + "Power",
                List.of(ChatColor.GRAY + "Ratio: " + String.format(java.util.Locale.US, "%.2f", powerState.ratio()),
                        ChatColor.GRAY + "Generation: " + String.format(java.util.Locale.US, "%.1f", powerState.generation()),
                        ChatColor.GRAY + "Consumption: " + String.format(java.util.Locale.US, "%.1f", powerState.consumption()),
                        ChatColor.GRAY + "Battery: " + powerState.batteryStored() + "/" + String.format(java.util.Locale.US, "%.0f", powerState.batteryCapacity()))));
        inventory.setItem(14, icon(Material.EMERALD, ChatColor.GREEN + "Economy",
                List.of(ChatColor.GRAY + "Debt: " + island.maintenanceDebt(),
                        ChatColor.GRAY + "Status: " + island.maintenanceStatus(),
                        ChatColor.GRAY + "Reputation: " + island.reputation())));
        inventory.setItem(16, icon(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "Research",
                List.of(ChatColor.GRAY + "Points: " + island.researchPoints(),
                        ChatColor.GRAY + "Agriculture x" + String.format(java.util.Locale.US, "%.2f", boosts.agricultureBoost()),
                        ChatColor.GRAY + "Machine slots +" + boosts.factorySlotBonus(),
                        ChatColor.GRAY + "Contract slots +" + boosts.contractSlotBonus())));
        holder.action(16, "main_research", "");
        holder.action(20, "main_contracts", "");
        inventory.setItem(20, icon(Material.WRITABLE_BOOK, ChatColor.GOLD + "Contracts",
                List.of(ChatColor.GRAY + "Open delivery contracts.")));
        holder.action(22, "main_market", "");
        inventory.setItem(22, icon(Material.EMERALD, ChatColor.GREEN + "Market",
                List.of(ChatColor.GRAY + "Sell stored factory items.")));
        holder.action(24, "main_storage", "");
        inventory.setItem(24, icon(Material.CHEST, ChatColor.YELLOW + "Storage",
                List.of(ChatColor.GRAY + "Browse island virtual storage.")));
        player.openInventory(inventory);
    }

    public void openStorage(Player player, FactoryIsland island) {
        openStorage(player, island, 0);
    }

    public void openStorage(Player player, FactoryIsland island, int page) {
        int safePage = Math.max(0, page);
        FactoryGuiHolder holder = new FactoryGuiHolder("storage", island.islandUuid(), null, safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54, title("storage-title", "Factory Storage"));
        holder.inventory(inventory);
        VirtualInventory virtual = storage.islandStorage(island.islandUuid());
        List<Map.Entry<String, Long>> entries = virtual.items().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList();
        int pageSize = 45;
        int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
        if (safePage > maxPage) {
            openStorage(player, island, maxPage);
            return;
        }
        holder.action(45, "storage_page", String.valueOf(Math.max(0, safePage - 1)));
        inventory.setItem(45, icon(Material.ARROW, ChatColor.YELLOW + "Previous Page",
                List.of(ChatColor.GRAY + "Page " + (safePage + 1) + " of " + (maxPage + 1))));
        inventory.setItem(49, icon(Material.BOOK, ChatColor.AQUA + "Storage",
                List.of(ChatColor.GRAY + "Used: " + virtual.used() + "/" + virtual.capacity(),
                        ChatColor.GRAY + "Page: " + (safePage + 1) + "/" + (maxPage + 1))));
        holder.action(53, "deposit_hand", "");
        inventory.setItem(53, icon(Material.HOPPER, ChatColor.GREEN + "Deposit Hand",
                List.of(ChatColor.GRAY + "Move the item stack in your hand into storage.")));
        holder.action(52, "storage_page", String.valueOf(Math.min(maxPage, safePage + 1)));
        inventory.setItem(52, icon(Material.ARROW, ChatColor.YELLOW + "Next Page",
                List.of(ChatColor.GRAY + "Page " + (safePage + 1) + " of " + (maxPage + 1))));
        int slot = 0;
        int start = safePage * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        for (Map.Entry<String, Long> entry : entries.subList(start, end)) {
            ItemRegistry.FactoryItem item = items.get(entry.getKey()).orElse(new ItemRegistry.FactoryItem(
                    entry.getKey(), Material.PAPER, entry.getKey(), 0, false, 0, List.of()));
            ItemStack stack = new ItemStack(item.material(), (int) Math.max(1, Math.min(64, entry.getValue())));
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName(ChatColor.WHITE + item.displayName());
            meta.setLore(List.of(ChatColor.GRAY + "Amount: " + entry.getValue(),
                    ChatColor.DARK_GRAY + "Left: 64, Right: 1, Shift: max"));
            stack.setItemMeta(meta);
            holder.action(slot, "withdraw_storage", entry.getKey());
            inventory.setItem(slot++, stack);
        }
        player.openInventory(inventory);
    }

    public void openMachine(Player player, MachineInstance machine) {
        FactoryGuiHolder holder = new FactoryGuiHolder("machine", machine.islandUuid(), machine.machineId());
        Inventory inventory = Bukkit.createInventory(holder, 27, title("machine-title", "Machine"));
        holder.inventory(inventory);
        MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Type: " + machine.typeId());
        lore.add(messages.raw("machine-status", Map.of("status", machine.status().name())));
        lore.add(ChatColor.GRAY + "Wear: " + String.format(java.util.Locale.US, "%.2f", machine.wear()));
        lore.add(ChatColor.GRAY + "Island: " + machine.islandUuid());
        if (definition != null) {
            lore.add(ChatColor.GRAY + "Power: " + definition.powerConsumption());
            lore.add(ChatColor.GRAY + "Tier: " + definition.tier());
            if (!definition.requiredUnlocks().isEmpty()) {
                lore.add(ChatColor.GRAY + "Requires: " + definition.requiredUnlocks());
            }
            if (definition.isLogistics()) {
                lore.add(ChatColor.GRAY + "Throughput: " + definition.logisticsThroughput() + "/cycle");
            }
        }
        storage.get(machine.inputInventoryId()).ifPresent(input -> {
            lore.add(ChatColor.GRAY + "Input: " + input.used() + "/" + input.capacity());
            if (!input.items().isEmpty()) {
                lore.add(ChatColor.DARK_GRAY + "In: " + input.items());
            }
        });
        storage.get(machine.outputInventoryId()).ifPresent(output -> {
            lore.add(ChatColor.GRAY + "Output: " + output.used() + "/" + output.capacity());
            if (!output.items().isEmpty()) {
                lore.add(ChatColor.DARK_GRAY + "Out: " + output.items());
            }
        });
        if (machine.linkedResourceNodeId() != null) {
            lore.add(ChatColor.GRAY + "Node: " + machine.linkedResourceNodeId());
        }
        ItemStack info = new ItemStack(definition == null ? Material.STONE : definition.material());
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + (definition == null ? machine.typeId() : definition.displayName()));
        meta.setLore(lore);
        info.setItemMeta(meta);
        inventory.setItem(13, info);
        addRecipeSelectors(holder, inventory, machine, definition);
        holder.action(20, "deposit_machine_input", "");
        inventory.setItem(20, icon(Material.HOPPER, ChatColor.GREEN + "Deposit Input",
                List.of(ChatColor.GRAY + "Move the item stack in your hand into this machine input.")));
        holder.action(22, "withdraw_machine_input", "");
        inventory.setItem(22, icon(Material.DROPPER, ChatColor.YELLOW + "Take Input",
                List.of(ChatColor.GRAY + "Withdraw up to one stack from this machine input.")));
        holder.action(24, "withdraw_machine_output", "");
        inventory.setItem(24, icon(Material.CHEST, ChatColor.AQUA + "Take Output",
                List.of(ChatColor.GRAY + "Withdraw up to one stack from this machine output.")));
        holder.action(26, "reclaim_machine", "");
        inventory.setItem(26, icon(Material.BARRIER, ChatColor.RED + "Reclaim Machine",
                List.of(ChatColor.GRAY + "Return buffers to island storage and pick up this machine.")));
        player.openInventory(inventory);
    }

    private void addRecipeSelectors(FactoryGuiHolder holder, Inventory inventory, MachineInstance machine, MachineDefinition definition) {
        if (definition == null) {
            return;
        }
        FactoryIsland island = islands.find(machine.islandUuid()).orElse(null);
        if (island == null) {
            return;
        }
        List<RecipeDefinition> availableRecipes = recipes.recipesFor(machine.typeId()).stream()
                .filter(recipe -> definition.allowedRecipes().isEmpty() || definition.allowedRecipes().contains(recipe.id()))
                .filter(recipe -> recipe.minTier() <= island.tier())
                .filter(recipe -> recipe.researchRequired().isEmpty() || research.unlocked(island).containsAll(recipe.researchRequired()))
                .toList();
        if (availableRecipes.isEmpty()) {
            return;
        }
        String selectedRecipeId = machine.selectedRecipeId();
        holder.action(0, "select_recipe", "");
        inventory.setItem(0, icon(selectedRecipeId == null || selectedRecipeId.isBlank() ? Material.LIME_DYE : Material.GRAY_DYE,
                ChatColor.AQUA + "Auto Recipe",
                List.of(ChatColor.GRAY + "Machine chooses the first runnable recipe.")));
        int slot = 1;
        for (RecipeDefinition recipe : availableRecipes) {
            if (slot >= 9) {
                break;
            }
            boolean selected = recipe.id().equals(selectedRecipeId);
            holder.action(slot, "select_recipe", recipe.id());
            inventory.setItem(slot++, icon(selected ? Material.LIME_DYE : Material.PAPER,
                    (selected ? ChatColor.GREEN : ChatColor.YELLOW) + recipe.id(),
                    List.of(ChatColor.GRAY + "Input: " + recipe.input(),
                            ChatColor.GRAY + "Output: " + recipe.output(),
                            ChatColor.GRAY + "Byproducts: " + recipe.byproducts())));
        }
    }

    public void openContracts(Player player, FactoryIsland island, ContractService contracts) {
        FactoryGuiHolder holder = new FactoryGuiHolder("contracts", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("contracts-title", "Factory Contracts"));
        holder.inventory(inventory);
        int slot = 10;
        List<ContractService.ActiveContract> activeContracts = contracts.activeContracts(island);
        inventory.setItem(4, icon(Material.CLOCK, ChatColor.AQUA + "Active Contracts",
                List.of(ChatColor.GRAY + "Open: " + activeContracts.size())));
        for (ContractService.ActiveContract active : activeContracts) {
            if (slot >= 17) {
                break;
            }
            ContractService.ContractTemplate template = active.template();
            holder.action(slot, "contract_detail", active.contractId().toString());
            inventory.setItem(slot++, icon(Material.WRITABLE_BOOK, ChatColor.GOLD + template.id(),
                    List.of(ChatColor.GRAY + "Type: " + template.type(),
                            ChatColor.GRAY + "Tier: " + template.tier(),
                            ChatColor.GRAY + "Required: " + template.required(),
                            ChatColor.GRAY + "Money: " + template.money(),
                            ChatColor.GRAY + "Research: " + template.research(),
                            ChatColor.GRAY + "Reputation: " + template.reputation(),
                            ChatColor.GRAY + "Items: " + template.itemRewards(),
                            ChatColor.GRAY + "Expires: " + Math.max(0, (active.expiresAt() - System.currentTimeMillis()) / 60000) + "m")));
        }
        contracts.emergencyTemplate().ifPresent(template -> {
            int used = contracts.emergencyUsedToday(island);
            boolean available = island.maintenanceDebt() > 0 && used < contracts.emergencyDailyLimit();
            if (available) {
                holder.action(22, "complete_emergency", "");
            }
            inventory.setItem(22, icon(available ? Material.FIREWORK_STAR : Material.GRAY_DYE,
                    (available ? ChatColor.RED : ChatColor.GRAY) + "Emergency Contract",
                    List.of(ChatColor.GRAY + "Debt: " + island.maintenanceDebt(),
                            ChatColor.GRAY + "Used today: " + used + "/" + contracts.emergencyDailyLimit(),
                            ChatColor.GRAY + "Required: " + template.required(),
                            ChatColor.GRAY + "Debt relief: " + template.debtRelief(),
                            ChatColor.GRAY + (available ? "Click to deliver from island storage." : "No emergency delivery is available."))));
        });
        player.openInventory(inventory);
    }

    public void openContractDetail(Player player, FactoryIsland island, ContractService contracts, java.util.UUID contractId) {
        ContractService.ActiveContract active = contracts.activeContracts(island).stream()
                .filter(contract -> contract.contractId().equals(contractId))
                .findFirst()
                .orElse(null);
        if (active == null) {
            openContracts(player, island, contracts);
            return;
        }
        ContractService.ContractTemplate template = active.template();
        FactoryGuiHolder holder = new FactoryGuiHolder("contract-detail", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("contract-detail-title", "Contract Detail"));
        holder.inventory(inventory);
        inventory.setItem(4, icon(Material.WRITABLE_BOOK, ChatColor.GOLD + template.id(),
                List.of(ChatColor.GRAY + "Type: " + template.type(),
                        ChatColor.GRAY + "Tier: " + template.tier(),
                        ChatColor.GRAY + "Expires: " + Math.max(0, (active.expiresAt() - System.currentTimeMillis()) / 60000) + "m")));
        inventory.setItem(11, icon(Material.CHEST, ChatColor.YELLOW + "Required Items",
                contractLines(template.required(), "No items required.")));
        inventory.setItem(15, icon(Material.EMERALD, ChatColor.GREEN + "Rewards",
                rewardLines(template)));
        holder.action(18, "contracts_back", "");
        inventory.setItem(18, icon(Material.ARROW, ChatColor.YELLOW + "Back",
                List.of(ChatColor.GRAY + "Return to contract list.")));
        holder.action(22, "complete_contract", active.contractId().toString());
        inventory.setItem(22, icon(Material.LIME_DYE, ChatColor.GREEN + "Deliver Contract",
                List.of(ChatColor.GRAY + "Submit required items from island storage.")));
        player.openInventory(inventory);
    }

    private List<String> contractLines(Map<String, Long> values, String emptyText) {
        if (values.isEmpty()) {
            return List.of(ChatColor.GRAY + emptyText);
        }
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> ChatColor.GRAY + entry.getKey() + " x" + entry.getValue())
                .toList();
    }

    private List<String> rewardLines(ContractService.ContractTemplate template) {
        List<String> lore = new ArrayList<>();
        if (template.money() > 0) {
            lore.add(ChatColor.GRAY + "Money: " + template.money());
        }
        if (template.research() > 0) {
            lore.add(ChatColor.GRAY + "Research: " + template.research());
        }
        if (template.reputation() > 0) {
            lore.add(ChatColor.GRAY + "Reputation: " + template.reputation());
        }
        if (template.debtRelief() > 0) {
            lore.add(ChatColor.GRAY + "Debt relief: " + template.debtRelief());
        }
        template.itemRewards().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> ChatColor.GRAY + entry.getKey() + " x" + entry.getValue())
                .forEach(lore::add);
        return lore.isEmpty() ? List.of(ChatColor.GRAY + "No rewards.") : lore;
    }

    public void openMarket(Player player, FactoryIsland island, MarketService market) {
        openMarket(player, island, market, 0);
    }

    public void openMarket(Player player, FactoryIsland island, MarketService market, int page) {
        int safePage = Math.max(0, page);
        FactoryGuiHolder holder = new FactoryGuiHolder("market", island.islandUuid(), null, safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54, title("market-title", "Factory Market"));
        holder.inventory(inventory);
        List<String> itemIds = market.prices().keySet().stream().sorted().toList();
        int pageSize = 45;
        int maxPage = Math.max(0, (itemIds.size() - 1) / pageSize);
        if (safePage > maxPage) {
            openMarket(player, island, market, maxPage);
            return;
        }
        holder.action(45, "market_page", String.valueOf(Math.max(0, safePage - 1)));
        inventory.setItem(45, icon(Material.ARROW, ChatColor.YELLOW + "Previous Page",
                List.of(ChatColor.GRAY + "Page " + (safePage + 1) + " of " + (maxPage + 1))));
        inventory.setItem(49, icon(Material.EMERALD, ChatColor.GREEN + "Market",
                List.of(ChatColor.GRAY + "Debt: " + island.maintenanceDebt(),
                        ChatColor.GRAY + "Page: " + (safePage + 1) + "/" + (maxPage + 1))));
        holder.action(52, "market_page", String.valueOf(Math.min(maxPage, safePage + 1)));
        inventory.setItem(52, icon(Material.ARROW, ChatColor.YELLOW + "Next Page",
                List.of(ChatColor.GRAY + "Page " + (safePage + 1) + " of " + (maxPage + 1))));
        int start = safePage * pageSize;
        int end = Math.min(itemIds.size(), start + pageSize);
        int slot = 0;
        VirtualInventory virtual = storage.islandStorage(island.islandUuid());
        for (String itemId : itemIds.subList(start, end)) {
            ItemRegistry.FactoryItem item = items.get(itemId).orElse(new ItemRegistry.FactoryItem(
                    itemId, Material.PAPER, itemId, 0, false, market.prices().getOrDefault(itemId, 0L), List.of()));
            long stored = virtual.amount(itemId);
            long unitPrice = market.price(island.islandUuid(), itemId, 1);
            ItemStack stack = new ItemStack(item.material(), (int) Math.max(1, Math.min(64, stored)));
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName((stored > 0 ? ChatColor.GREEN : ChatColor.GRAY) + item.displayName());
            meta.setLore(List.of(ChatColor.GRAY + "Stored: " + stored,
                    ChatColor.GRAY + "Current price: " + unitPrice,
                    ChatColor.DARK_GRAY + "Left: 64, Right: 1, Shift: max"));
            stack.setItemMeta(meta);
            holder.action(slot, "sell_market_item", itemId);
            inventory.setItem(slot++, stack);
        }
        player.openInventory(inventory);
    }

    public void openResearch(Player player, FactoryIsland island, ResearchService research) {
        FactoryGuiHolder holder = new FactoryGuiHolder("research", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("research-title", "Factory Research"));
        holder.inventory(inventory);
        int slot = 10;
        Set<String> unlockedIds = research.unlocked(island);
        double balance = economy.balance(player);
        for (UnlockDefinition unlock : research.all().values()) {
            if (slot >= 17) {
                break;
            }
            boolean unlocked = unlockedIds.contains(unlock.id());
            boolean hasRequiredUnlocks = unlockedIds.containsAll(unlock.requires());
            boolean hasPoints = island.researchPoints() >= unlock.cost();
            boolean hasReputation = island.reputation() >= unlock.requiredReputation();
            boolean hasMoney = balance >= unlock.moneyCost();
            boolean ready = !unlocked && hasRequiredUnlocks && hasPoints && hasReputation && hasMoney;
            holder.action(slot, "unlock_research", unlock.id());
            inventory.setItem(slot++, icon(unlocked ? Material.LIME_DYE : (ready ? Material.YELLOW_DYE : Material.GRAY_DYE),
                    (unlocked ? ChatColor.GREEN : ChatColor.YELLOW) + unlock.displayName(),
                    List.of(ChatColor.GRAY + "Research: " + island.researchPoints() + "/" + unlock.cost(),
                            ChatColor.GRAY + "Money: " + String.format(java.util.Locale.US, "%.0f", balance) + "/" + unlock.moneyCost(),
                            ChatColor.GRAY + "Reputation: " + island.reputation() + "/" + unlock.requiredReputation(),
                            ChatColor.GRAY + "Id: " + unlock.id(),
                            ChatColor.GRAY + "Requires: " + unlock.requires(),
                            ChatColor.GRAY + "Unlocks: " + unlock.grants(),
                            ChatColor.GRAY + "Factory tier: " + (unlock.factoryTier() > 0 ? unlock.factoryTier() : "-"),
                            ChatColor.GRAY + "Prerequisites: " + (hasRequiredUnlocks ? "Ready" : "Missing"),
                            ChatColor.GRAY + "Status: " + (unlocked ? "Unlocked" : (ready ? "Ready" : "Locked")))));
        }
        inventory.setItem(22, icon(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "Research Points",
                List.of(ChatColor.GRAY + String.valueOf(island.researchPoints()))));
        player.openInventory(inventory);
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private String title(String key, String fallback) {
        String value = messages.raw(key);
        return value.equals(key) ? fallback : value;
    }
}
