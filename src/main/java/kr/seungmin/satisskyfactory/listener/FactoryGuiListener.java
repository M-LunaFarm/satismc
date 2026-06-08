package kr.seungmin.satisskyfactory.listener;

import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiHolder;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.research.ResearchService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.UUID;

public final class FactoryGuiListener implements Listener {
    private final FactoryIslandService islands;
    private final ContractService contracts;
    private final ResearchService research;
    private final FactoryGuiService gui;

    public FactoryGuiListener(FactoryIslandService islands, ContractService contracts, ResearchService research, FactoryGuiService gui) {
        this.islands = islands;
        this.contracts = contracts;
        this.research = research;
        this.gui = gui;
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
        holder.action(event.getRawSlot()).ifPresent(action -> handle(player, holder, action));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof FactoryGuiHolder) {
            // State is already persisted by the services that mutate virtual inventories.
        }
    }

    private void handle(Player player, FactoryGuiHolder holder, FactoryGuiHolder.GuiAction action) {
        FactoryIsland island = islands.find(holder.islandUuid()).orElse(null);
        if (island == null) {
            player.sendMessage("Factory island is not loaded.");
            return;
        }
        if (action.type().equals("unlock_research")) {
            ResearchService.UnlockResult result = research.unlock(island, action.value());
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
        }
    }
}
