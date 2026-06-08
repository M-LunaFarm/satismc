package kr.seungmin.satisskyfactory.gui;

import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
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
import java.util.List;
import java.util.Map;

public final class FactoryGuiService {
    private final StorageService storage;
    private final ItemRegistry items;
    private final MachineDefinitionService definitions;

    public FactoryGuiService(StorageService storage, ItemRegistry items, MachineDefinitionService definitions) {
        this.storage = storage;
        this.items = items;
        this.definitions = definitions;
    }

    public void openMain(Player player, FactoryIsland island, int machineCount, PowerNetworkService.NetworkState powerState,
                         IslandBoostService.Boosts boosts) {
        FactoryGuiHolder holder = new FactoryGuiHolder("main", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, "SatisSkyFactory");
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
        player.openInventory(inventory);
    }

    public void openStorage(Player player, FactoryIsland island) {
        FactoryGuiHolder holder = new FactoryGuiHolder("storage", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 54, "Factory Storage");
        holder.inventory(inventory);
        VirtualInventory virtual = storage.islandStorage(island.islandUuid());
        int slot = 0;
        for (Map.Entry<String, Long> entry : virtual.items().entrySet()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            ItemRegistry.FactoryItem item = items.get(entry.getKey()).orElse(new ItemRegistry.FactoryItem(entry.getKey(), Material.PAPER, entry.getKey()));
            ItemStack stack = new ItemStack(item.material(), (int) Math.max(1, Math.min(64, entry.getValue())));
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName(ChatColor.WHITE + item.displayName());
            meta.setLore(List.of(ChatColor.GRAY + "Amount: " + entry.getValue()));
            stack.setItemMeta(meta);
            holder.action(slot, "withdraw_storage", entry.getKey());
            inventory.setItem(slot++, stack);
        }
        player.openInventory(inventory);
    }

    public void openMachine(Player player, MachineInstance machine) {
        FactoryGuiHolder holder = new FactoryGuiHolder("machine", machine.islandUuid(), machine.machineId());
        Inventory inventory = Bukkit.createInventory(holder, 27, "Machine");
        holder.inventory(inventory);
        MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Type: " + machine.typeId());
        lore.add(ChatColor.GRAY + "Status: " + machine.status().name());
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
        storage.get(machine.inputInventoryId()).ifPresent(input ->
                lore.add(ChatColor.GRAY + "Input: " + input.used() + "/" + input.capacity()));
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
        player.openInventory(inventory);
    }

    public void openContracts(Player player, FactoryIsland island, ContractService contracts) {
        FactoryGuiHolder holder = new FactoryGuiHolder("contracts", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, "Factory Contracts");
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
            holder.action(slot, "complete_contract", active.contractId().toString());
            inventory.setItem(slot++, icon(Material.WRITABLE_BOOK, ChatColor.GOLD + template.id(),
                    List.of(ChatColor.GRAY + "Type: " + template.type(),
                            ChatColor.GRAY + "Tier: " + template.tier(),
                            ChatColor.GRAY + "Required: " + template.required(),
                            ChatColor.GRAY + "Money: " + template.money(),
                            ChatColor.GRAY + "Research: " + template.research(),
                            ChatColor.GRAY + "Reputation: " + template.reputation(),
                            ChatColor.GRAY + "Expires: " + Math.max(0, (active.expiresAt() - System.currentTimeMillis()) / 60000) + "m")));
        }
        player.openInventory(inventory);
    }

    public void openResearch(Player player, FactoryIsland island, ResearchService research) {
        FactoryGuiHolder holder = new FactoryGuiHolder("research", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, "Factory Research");
        holder.inventory(inventory);
        int slot = 10;
        for (ResearchService.ResearchUnlock unlock : research.all().values()) {
            if (slot >= 17) {
                break;
            }
            boolean unlocked = research.unlocked(island).contains(unlock.id());
            holder.action(slot, "unlock_research", unlock.id());
            inventory.setItem(slot++, icon(unlocked ? Material.LIME_DYE : Material.GRAY_DYE,
                    (unlocked ? ChatColor.GREEN : ChatColor.YELLOW) + unlock.id(),
                    List.of(ChatColor.GRAY + "Cost: " + unlock.cost(),
                            ChatColor.GRAY + "Requires: " + unlock.requires(),
                            ChatColor.GRAY + "Factory tier: " + (unlock.factoryTier() > 0 ? unlock.factoryTier() : "-"),
                            ChatColor.GRAY + "Status: " + (unlocked ? "Unlocked" : "Locked"))));
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
}
