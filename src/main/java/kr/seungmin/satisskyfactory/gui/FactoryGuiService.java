package kr.seungmin.satisskyfactory.gui;

import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
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
        lore.add(ChatColor.GRAY + "Island: " + machine.islandUuid());
        if (definition != null) {
            lore.add(ChatColor.GRAY + "Power: " + definition.powerConsumption());
            lore.add(ChatColor.GRAY + "Tier: " + definition.tier());
        }
        ItemStack info = new ItemStack(definition == null ? Material.STONE : definition.material());
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + (definition == null ? machine.typeId() : definition.displayName()));
        meta.setLore(lore);
        info.setItemMeta(meta);
        inventory.setItem(13, info);
        player.openInventory(inventory);
    }
}
