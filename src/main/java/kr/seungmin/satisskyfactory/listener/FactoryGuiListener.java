package kr.seungmin.satisskyfactory.listener;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiHolder;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.recipe.RecipeDefinition;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FactoryGuiListener implements Listener {
    private final FactoryIslandService islands;
    private final SuperiorSkyblockHook skyblock;
    private final ContractService contracts;
    private final ResearchService research;
    private final FactoryGuiService gui;
    private final MachineService machines;
    private final RecipeService recipes;
    private final StorageService storage;
    private final ItemRegistry items;
    private final CustomItemFactory itemFactory;
    private final MarketService market;
    private final MachineDefinitionService definitions;
    private final MaintenanceService maintenance;
    private final MessageService messages;

    public FactoryGuiListener(FactoryIslandService islands, SuperiorSkyblockHook skyblock, ContractService contracts, ResearchService research, FactoryGuiService gui,
                              MachineService machines, RecipeService recipes, StorageService storage, ItemRegistry items, CustomItemFactory itemFactory,
                              MarketService market, MachineDefinitionService definitions, MaintenanceService maintenance,
                              MessageService messages) {
        this.islands = islands;
        this.skyblock = skyblock;
        this.contracts = contracts;
        this.research = research;
        this.gui = gui;
        this.machines = machines;
        this.recipes = recipes;
        this.storage = storage;
        this.items = items;
        this.itemFactory = itemFactory;
        this.market = market;
        this.definitions = definitions;
        this.maintenance = maintenance;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof FactoryGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        holder.action(event.getRawSlot()).ifPresent(action -> handle(player, holder, action, event));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof FactoryGuiHolder) {
            // State is already persisted by the services that mutate virtual inventories.
        }
    }

    private void handle(Player player, FactoryGuiHolder holder, FactoryGuiHolder.GuiAction action, InventoryClickEvent event) {
        FactoryIsland island = islands.find(holder.islandUuid()).orElse(null);
        if (island == null) {
            messages.send(player, "island-not-loaded");
            return;
        }
        if (!canUseIslandGui(player, island)) {
            messages.send(player, "not-member");
            player.closeInventory();
            return;
        }
        if (action.type().equals("storage_page")) {
            gui.openStorage(player, island, parsePage(action.value()));
            return;
        }
        if (action.type().equals("market_page")) {
            gui.openMarket(player, island, market, parsePage(action.value()));
            return;
        }
        if (action.type().equals("main_storage")) {
            gui.openStorage(player, island);
            return;
        }
        if (action.type().equals("main_contracts")) {
            gui.openContracts(player, island, contracts);
            return;
        }
        if (action.type().equals("main_research")) {
            gui.openResearch(player, island, research);
            return;
        }
        if (action.type().equals("main_market")) {
            gui.openMarket(player, island, market);
            return;
        }
        if (action.type().equals("contracts_back")) {
            gui.openContracts(player, island, contracts);
            return;
        }
        if (action.type().equals("contract_detail")) {
            try {
                gui.openContractDetail(player, island, contracts, UUID.fromString(action.value()));
            } catch (IllegalArgumentException exception) {
                messages.send(player, "invalid-contract");
                gui.openContracts(player, island, contracts);
            }
            return;
        }
        if (action.type().equals("unlock_research")) {
            ResearchService.UnlockResult result = research.unlock(island, player, action.value());
            islands.save(island);
            messages.send(player, "research-unlock-result", Map.of("result", result.name()));
            gui.openResearch(player, island, research);
            return;
        }
        if (action.type().equals("complete_contract")) {
            try {
                UUID contractId = UUID.fromString(action.value());
                contracts.completeContract(island, player, contractId).ifPresentOrElse(active -> {
                    refreshMaintenanceStatus(island);
                    messages.send(player, "contract-completed", Map.of("contract", active.template().id()));
                }, () -> messages.send(player, "contract-requirements-missing"));
                gui.openContracts(player, island, contracts);
            } catch (IllegalArgumentException exception) {
                messages.send(player, "invalid-contract");
            }
            return;
        }
        if (action.type().equals("complete_emergency")) {
            if (contracts.completeEmergency(island, player)) {
                maintenance.updateStatus(island);
                islands.save(island);
                messages.send(player, "emergency-contract-completed");
            } else {
                messages.send(player, "emergency-contract-unavailable");
            }
            gui.openContracts(player, island, contracts);
            return;
        }
        if (action.type().equals("withdraw_storage")) {
            withdrawStorageItem(player, island, action.value(), holder.page(), withdrawAmount(event));
            return;
        }
        if (action.type().equals("sell_market_item")) {
            sellMarketItem(player, island, action.value(), holder.page(), withdrawAmount(event));
            return;
        }
        if (action.type().equals("deposit_hand")) {
            depositHand(player, island);
            return;
        }
        if (action.type().equals("deposit_machine_input")) {
            machine(holder).ifPresentOrElse(machine -> depositMachineInput(player, machine),
                    () -> messages.send(player, "machine-unavailable"));
            return;
        }
        if (action.type().equals("withdraw_machine_input")) {
            machine(holder).ifPresentOrElse(machine -> withdrawMachineInventory(player, machine, true),
                    () -> messages.send(player, "machine-unavailable"));
            return;
        }
        if (action.type().equals("withdraw_machine_output")) {
            machine(holder).ifPresentOrElse(machine -> withdrawMachineInventory(player, machine, false),
                    () -> messages.send(player, "machine-unavailable"));
            return;
        }
        if (action.type().equals("select_recipe")) {
            machine(holder).ifPresentOrElse(machine -> selectRecipe(player, machine, action.value()),
                    () -> messages.send(player, "machine-unavailable"));
            return;
        }
        if (action.type().equals("reclaim_machine")) {
            machine(holder).ifPresentOrElse(machine -> reclaimMachine(player, island, machine),
                    () -> messages.send(player, "machine-unavailable"));
        }
    }

    private Optional<MachineInstance> machine(FactoryGuiHolder holder) {
        UUID machineId = holder.machineId();
        return machineId == null ? Optional.empty() : machines.find(machineId);
    }

    private boolean canUseIslandGui(Player player, FactoryIsland island) {
        return skyblock.getIslandByUuid(island.islandUuid())
                .map(ref -> skyblock.isPlayerIslandMember(player, ref))
                .orElseGet(() -> skyblock.getIslandOf(player)
                        .filter(ref -> ref.islandUuid().equals(island.islandUuid()))
                        .map(ref -> skyblock.isPlayerIslandMember(player, ref))
                        .orElse(false));
    }

    private void withdrawStorageItem(Player player, FactoryIsland island, String itemId, int page, long requested) {
        if (items.get(itemId).map(ItemRegistry.FactoryItem::virtualOnly).orElse(false)) {
            messages.send(player, "virtual-only-withdraw");
            gui.openStorage(player, island, page);
            return;
        }
        var inventory = storage.islandStorage(island.islandUuid());
        long amount = Math.min(requested, inventory.amount(itemId));
        if (amount <= 0 || !inventory.remove(itemId, amount)) {
            messages.send(player, "item-unavailable");
            gui.openStorage(player, island, page);
            return;
        }
        long returned = giveVirtualItem(player, itemId, amount);
        if (returned > 0) {
            inventory.add(itemId, returned);
            messages.send(player, "inventory-full");
        }
        storage.save(inventory);
        messages.send(player, "withdrew", Map.of("item", itemId, "amount", String.valueOf(amount - returned)));
        gui.openStorage(player, island, page);
    }

    private void depositHand(Player player, FactoryIsland island) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            messages.send(player, "hold-item-first");
            return;
        }
        String itemId = itemIdForHand(hand);
        long amount = hand.getAmount();
        var inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.add(itemId, amount)) {
            messages.send(player, "storage-full");
            return;
        }
        hand.setAmount(0);
        storage.save(inventory);
        messages.send(player, "deposited", Map.of("item", itemId, "amount", String.valueOf(amount)));
        gui.openStorage(player, island);
    }

    private void sellMarketItem(Player player, FactoryIsland island, String itemId, int page, long requested) {
        long stored = storage.islandStorage(island.islandUuid()).amount(itemId);
        long amount = Math.min(requested, stored);
        if (amount <= 0) {
            messages.send(player, "nothing-to-sell");
            gui.openMarket(player, island, market, page);
            return;
        }
        market.sell(island, player, itemId, amount).ifPresentOrElse(result -> {
            messages.send(player, "sold", Map.of("item", itemId, "amount", String.valueOf(amount), "money", String.valueOf(result.paidToPlayer())));
            if (result.debtRepaid() > 0) {
                refreshMaintenanceStatus(island);
                messages.send(player, "debt-repaid", Map.of("amount", String.valueOf(result.debtRepaid())));
            }
        }, () -> messages.send(player, "cannot-sell"));
        islands.save(island);
        gui.openMarket(player, island, market, page);
    }

    private void refreshMaintenanceStatus(FactoryIsland island) {
        maintenance.updateStatus(island);
        islands.save(island);
    }

    private void depositMachineInput(Player player, MachineInstance machine) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            messages.send(player, "hold-item-first");
            return;
        }
        VirtualInventory inventory = storage.get(machine.inputInventoryId()).orElse(null);
        if (inventory == null) {
            messages.send(player, "machine-input-missing");
            return;
        }
        String itemId = itemIdForHand(hand);
        long amount = hand.getAmount();
        if (!inventory.add(itemId, amount)) {
            messages.send(player, "machine-input-full");
            return;
        }
        hand.setAmount(0);
        storage.save(inventory);
        messages.send(player, "deposited", Map.of("item", itemId, "amount", String.valueOf(amount)));
        gui.openMachine(player, machine);
    }

    private void withdrawMachineInventory(Player player, MachineInstance machine, boolean input) {
        UUID inventoryId = input ? machine.inputInventoryId() : machine.outputInventoryId();
        VirtualInventory inventory = storage.get(inventoryId).orElse(null);
        if (inventory == null || inventory.items().isEmpty()) {
            messages.send(player, "no-items-to-withdraw");
            gui.openMachine(player, machine);
            return;
        }
        Map.Entry<String, Long> entry = inventory.items().entrySet().iterator().next();
        String itemId = entry.getKey();
        long amount = Math.min(64, entry.getValue());
        if (amount <= 0 || !inventory.remove(itemId, amount)) {
            messages.send(player, "no-items-to-withdraw");
            gui.openMachine(player, machine);
            return;
        }
        ItemStack stack = items.get(itemId)
                .map(item -> itemFactory.factoryItem(item, (int) amount))
                .orElseGet(() -> new ItemStack(material(itemId), (int) amount));
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        long returned = overflow.values().stream().mapToLong(ItemStack::getAmount).sum();
        if (returned > 0) {
            inventory.add(itemId, returned);
            messages.send(player, "inventory-full");
        }
        storage.save(inventory);
        messages.send(player, "withdrew", Map.of("item", itemId, "amount", String.valueOf(amount - returned)));
        gui.openMachine(player, machine);
    }

    private long giveVirtualItem(Player player, String itemId, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            int stackAmount = (int) Math.min(64, remaining);
            ItemStack stack = items.get(itemId)
                    .map(item -> itemFactory.factoryItem(item, stackAmount))
                    .orElseGet(() -> new ItemStack(material(itemId), stackAmount));
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                return overflow.values().stream().mapToLong(ItemStack::getAmount).sum() + Math.max(0, remaining - stackAmount);
            }
            remaining -= stackAmount;
        }
        return 0;
    }

    private String itemIdForHand(ItemStack stack) {
        Optional<String> pdcItemId = itemFactory.factoryItemId(stack);
        if (pdcItemId.isPresent()) {
            return pdcItemId.get();
        }
        return items.itemIdForMaterial(stack.getType())
                .orElseGet(() -> stack.getType().name().toLowerCase(Locale.ROOT));
    }

    private long withdrawAmount(InventoryClickEvent event) {
        if (event.isShiftClick()) {
            return 64L * 36L;
        }
        return event.isRightClick() ? 1L : 64L;
    }

    private int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void selectRecipe(Player player, MachineInstance machine, String recipeId) {
        if (recipeId != null && !recipeId.isBlank() && !canSelectRecipe(machine, recipeId)) {
            messages.send(player, "recipe-unavailable");
            gui.openMachine(player, machine);
            return;
        }
        machine.selectedRecipeId(recipeId == null || recipeId.isBlank() ? null : recipeId);
        machines.save(machine);
        if (machine.selectedRecipeId() == null) {
            messages.send(player, "recipe-selection-cleared");
        } else {
            messages.send(player, "recipe-selected", Map.of("recipe", machine.selectedRecipeId()));
        }
        gui.openMachine(player, machine);
    }

    private boolean canSelectRecipe(MachineInstance machine, String recipeId) {
        MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
        FactoryIsland island = islands.find(machine.islandUuid()).orElse(null);
        if (definition == null || island == null) {
            return false;
        }
        return recipes.recipesFor(machine.typeId()).stream()
                .filter(recipe -> recipe.id().equals(recipeId))
                .anyMatch(recipe -> recipeAvailable(definition, island, recipe));
    }

    private boolean recipeAvailable(MachineDefinition definition, FactoryIsland island, RecipeDefinition recipe) {
        if (!definition.allowedRecipes().isEmpty() && !definition.allowedRecipes().contains(recipe.id())) {
            return false;
        }
        if (recipe.minTier() > island.tier()) {
            return false;
        }
        return recipe.researchRequired().isEmpty()
                || research.unlocked(island).containsAll(recipe.researchRequired());
    }

    private void reclaimMachine(Player player, FactoryIsland island, MachineInstance machine) {
        if (!machine.islandUuid().equals(island.islandUuid())) {
            messages.send(player, "machine-wrong-island");
            return;
        }
        MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
        if (definition == null) {
            messages.send(player, "machine-definition-missing");
            return;
        }
        if (!machines.remove(machine)) {
            messages.send(player, "machine-reclaim-storage-full");
            gui.openMachine(player, machine);
            return;
        }
        location(machine.location()).ifPresent(location -> location.getBlock().setType(Material.AIR, false));
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(itemFactory.machineItem(definition, 1));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.closeInventory();
        messages.send(player, "machine-reclaimed", Map.of("machine", definition.displayName()));
    }

    private Optional<Location> location(BlockKey key) {
        World world = Bukkit.getWorld(key.world());
        return world == null ? Optional.empty() : Optional.of(new Location(world, key.x(), key.y(), key.z()));
    }

    private Material material(String itemId) {
        Material material = Material.matchMaterial(itemId.toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }
}
