package com.citycore.npc.GUI;

import com.citycore.city.City;
import com.citycore.city.CityManager;
import com.citycore.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MayorGUI {

    public static final String GUI_TITLE = ChatColor.GOLD + "Alderic — Maire";

    public static final int SLOT_INFO    = 2;
    public static final int SLOT_FOLLOW  = 4;
    public static final int SLOT_EXPAND  = 6;

    private final CityManager cityManager;
    private final NPCManager npcManager;

    public MayorGUI(CityManager cityManager, NPCManager npcManager) {
        this.cityManager = cityManager;
        this.npcManager  = npcManager;
    }

    public void open(Player player) {
        City city = cityManager.getCity();
        if (city == null) return;

        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE);

        // Déco
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        // Bouton INFO
        inv.setItem(SLOT_INFO, makeItem(
                Material.BOOK,
                "§6📖 Informations",
                List.of(
                        "§7Niveau  : §f" + city.getLevel(),
                        "§7Caisse  : §6" + city.getCoins() + " coins",
                        "§7Chunks  : §f" + city.getClaimedChunks() + " §7/ §f" + city.getMaxChunks(),
                        "",
                        "§eCliquez pour afficher"
                )
        ));

        // Bouton SUIVI (toggle)
        boolean following = npcManager.isFollowing(player);
        inv.setItem(SLOT_FOLLOW, makeItem(
                following ? Material.REDSTONE : Material.LIME_DYE,
                following ? "§c⛔ Arrêter de suivre" : "§a👣 Demander de suivre",
                List.of(
                        following
                                ? "§7Alderic arrêtera de vous suivre."
                                : "§7Alderic vous suivra à ~2 blocs.",
                        "",
                        "§eCliquez pour " + (following ? "arrêter" : "activer")
                )
        ));

        // Bouton EXPAND
        int price    = cityManager.getNextExpandPrice();
        int balance  = cityManager.getCityCoins();
        boolean canAfford = cityManager.canAfford(price);

        inv.setItem(SLOT_EXPAND, makeItem(
                canAfford ? Material.EMERALD : Material.BARRIER,
                "§6🏗 Agrandir la ville",
                List.of(
                        "§7Ajoute §f+1 slot §7de chunk",
                        "",
                        "§7Prix   : §6" + price + " coins",
                        "§7Caisse : §6" + balance + " coins",
                        "",
                        canAfford ? "§aCliquez pour acheter" : "§cFonds insuffisants"
                )
        ));

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