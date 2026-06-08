package kr.seungmin.satisskyfactory.listener;

import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiHolder;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.Material;
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
    private final ContractService contracts;
    private final ResearchService research;
    private final FactoryGuiService gui;
    private final MachineService machines;
    private final StorageService storage;
    private final ItemRegistry items;
    private final CustomItemFactory itemFactory;

    public FactoryGuiListener(FactoryIslandService islands, ContractService contracts, ResearchService research, FactoryGuiService gui,
                              MachineService machines, StorageService storage, ItemRegistry items, CustomItemFactory itemFactory) {
        this.islands = islands;
        this.contracts = contracts;
        this.research = research;
        this.gui = gui;
        this.machines = machines;
        this.storage = storage;
        this.items = items;
        this.itemFactory = itemFactory;
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
            player.sendMessage("Factory island is not loaded.");
            return;
        }
        if (action.type().equals("storage_page")) {
            gui.openStorage(player, island, parsePage(action.value()));
            return;
        }
        if (action.type().equals("unlock_research")) {
            ResearchService.UnlockResult result = research.unlock(island, player, action.value());
            islands.save(island);
            player.sendMessage("Research unlock result: " + result.name());
            gui.openResearch(player, island, research);
            return;
        }
        if (action.type().equals("complete_contract")) {
            try {
                UUID contractId = UUID.fromString(action.value());
                contracts.completeContract(island, player, contractId).ifPresentOrElse(active -> {
                    islands.save(island);
                    player.sendMessage("Contract completed: " + active.template().id());
                }, () -> player.sendMessage("Contract requirements are missing."));
                gui.openContracts(player, island, contracts);
            } catch (IllegalArgumentException exception) {
                player.sendMessage("Invalid contract.");
            }
            return;
        }
        if (action.type().equals("withdraw_storage")) {
            withdrawStorageItem(player, island, action.value(), holder.page(), withdrawAmount(event));
            return;
        }
        if (action.type().equals("deposit_hand")) {
            depositHand(player, island);
            return;
        }
        if (action.type().equals("deposit_machine_input")) {
            machine(holder).ifPresentOrElse(machine -> depositMachineInput(player, machine),
                    () -> player.sendMessage("Machine is no longer available."));
            return;
        }
        if (action.type().equals("withdraw_machine_input")) {
            machine(holder).ifPresentOrElse(machine -> withdrawMachineInventory(player, machine, true),
                    () -> player.sendMessage("Machine is no longer available."));
            return;
        }
        if (action.type().equals("withdraw_machine_output")) {
            machine(holder).ifPresentOrElse(machine -> withdrawMachineInventory(player, machine, false),
                    () -> player.sendMessage("Machine is no longer available."));
            return;
        }
        if (action.type().equals("select_recipe")) {
            machine(holder).ifPresentOrElse(machine -> selectRecipe(player, machine, action.value()),
                    () -> player.sendMessage("Machine is no longer available."));
        }
    }

    private Optional<MachineInstance> machine(FactoryGuiHolder holder) {
        UUID machineId = holder.machineId();
        return machineId == null ? Optional.empty() : machines.find(machineId);
    }

    private void withdrawStorageItem(Player player, FactoryIsland island, String itemId, int page, long requested) {
        var inventory = storage.islandStorage(island.islandUuid());
        long amount = Math.min(requested, inventory.amount(itemId));
        if (amount <= 0 || !inventory.remove(itemId, amount)) {
            player.sendMessage("That item is no longer available.");
            gui.openStorage(player, island, page);
            return;
        }
        long returned = giveVirtualItem(player, itemId, amount);
        if (returned > 0) {
            inventory.add(itemId, returned);
            player.sendMessage("Your inventory is full.");
        }
        storage.save(inventory);
        player.sendMessage("Withdrew " + (amount - returned) + " " + itemId + ".");
        gui.openStorage(player, island, page);
    }

    private void depositHand(Player player, FactoryIsland island) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            player.sendMessage("Hold an item first.");
            return;
        }
        String itemId = itemFactory.factoryItemId(hand).orElseGet(() -> hand.getType().name().toLowerCase(Locale.ROOT));
        long amount = hand.getAmount();
        var inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.add(itemId, amount)) {
            player.sendMessage("Factory storage is full.");
            return;
        }
        hand.setAmount(0);
        storage.save(inventory);
        player.sendMessage("Deposited " + amount + " " + itemId + ".");
        gui.openStorage(player, island);
    }

    private void depositMachineInput(Player player, MachineInstance machine) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            player.sendMessage("Hold an item first.");
            return;
        }
        VirtualInventory inventory = storage.get(machine.inputInventoryId()).orElse(null);
        if (inventory == null) {
            player.sendMessage("Machine input is missing.");
            return;
        }
        String itemId = itemFactory.factoryItemId(hand).orElseGet(() -> hand.getType().name().toLowerCase(Locale.ROOT));
        long amount = hand.getAmount();
        if (!inventory.add(itemId, amount)) {
            player.sendMessage("Machine input is full.");
            return;
        }
        hand.setAmount(0);
        storage.save(inventory);
        player.sendMessage("Deposited " + amount + " " + itemId + ".");
        gui.openMachine(player, machine);
    }

    private void withdrawMachineInventory(Player player, MachineInstance machine, boolean input) {
        UUID inventoryId = input ? machine.inputInventoryId() : machine.outputInventoryId();
        VirtualInventory inventory = storage.get(inventoryId).orElse(null);
        if (inventory == null || inventory.items().isEmpty()) {
            player.sendMessage("No items to withdraw.");
            gui.openMachine(player, machine);
            return;
        }
        Map.Entry<String, Long> entry = inventory.items().entrySet().iterator().next();
        String itemId = entry.getKey();
        long amount = Math.min(64, entry.getValue());
        if (amount <= 0 || !inventory.remove(itemId, amount)) {
            player.sendMessage("No items to withdraw.");
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
            player.sendMessage("Your inventory is full.");
        }
        storage.save(inventory);
        player.sendMessage("Withdrew " + (amount - returned) + " " + itemId + ".");
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
        machine.selectedRecipeId(recipeId == null || recipeId.isBlank() ? null : recipeId);
        machines.save(machine);
        player.sendMessage(machine.selectedRecipeId() == null ? "Recipe selection cleared." : "Selected recipe: " + machine.selectedRecipeId());
        gui.openMachine(player, machine);
    }

    private Material material(String itemId) {
        Material material = Material.matchMaterial(itemId.toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }
}
