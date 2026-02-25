package com.citycore.building;

import com.citycore.building.BuildingManager;
import com.citycore.npc.CityNPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BuildingGUI {

    public static final String TITLE = "§8Bâtiments de la ville";

    private final BuildingManager buildingManager;

    public BuildingGUI(BuildingManager buildingManager) {
        this.buildingManager = buildingManager;
    }

    public void open(Player player) {
        List<Building> buildings = buildingManager.getAllBuildings();

        int size = Math.max(9, (int) Math.ceil(buildings.size() / 9.0) * 9);
        size = Math.min(size, 54); // Max 6 lignes
        Inventory inv = Bukkit.createInventory(null, size, TITLE);

        // Filler
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        int slot = 0;
        for (Building building : buildings) {
            if (slot >= size) break;

            String npcName = building.npcTag() != null
                    ? CityNPC.fromTag(building.npcTag()) != null
                    ? CityNPC.fromTag(building.npcTag()).displayName
                    : building.npcDisplayTag()
                    : "§7Aucun";

            List<String> lore = new ArrayList<>();
            lore.add("§7Zone : §f(" + building.x1() + "§7, §f" + building.z1()
                    + "§7) → §f(" + building.x2() + "§7, §f" + building.z2() + "§7)");
            lore.add("§7Monde : §f" + building.world());
            lore.add("");
            lore.add("§7NPC assigné : " + npcName);
            if (building.hasNpcPoint()) {
                lore.add("§d📍 Point NPC : §fX" + (int)(double) building.npcX()
                        + " §7Z" + (int)(double) building.npcZ());
            } else {
                lore.add("§8Aucun point NPC défini");
            }

            inv.setItem(slot++, makeItem(Material.BRICKS,
                    "§e🏛 " + building.name(), lore));
        }

        if (buildings.isEmpty()) {
            inv.setItem(4, makeItem(Material.BARRIER,
                    "§cAucun bâtiment",
                    List.of("§7Utilisez §f/city build new <nom>",
                            "§7pour créer un bâtiment.")));
        }

        player.openInventory(inv);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, String name) {
        return makeItem(mat, name, List.of());
    }
}