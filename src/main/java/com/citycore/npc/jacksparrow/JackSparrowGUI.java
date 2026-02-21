package com.citycore.npc.jacksparrow;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JackSparrowGUI {

    public static final String GUI_TITLE_MAIN = ChatColor.DARK_AQUA
            + CityNPC.JACKSPARROW.displayName + " — " + CityNPC.JACKSPARROW.function;
    public static final String GUI_TITLE_SELL = ChatColor.DARK_AQUA
            + CityNPC.JACKSPARROW.displayName + " — Vendre";
    public static final String GUI_TITLE_BUY  = ChatColor.DARK_AQUA
            + CityNPC.JACKSPARROW.displayName + " — Acheter";

    public static final int SLOT_SELL   = 2;
    public static final int SLOT_BUY    = 4;
    public static final int SLOT_FOLLOW = 6;
    public static final int SLOT_BACK   = 8;

    private final JackSparrowConfig config;
    private final NPCManager npcManager;

    public JackSparrowGUI(JackSparrowConfig config, NPCManager npcManager) {
        this.config     = config;
        this.npcManager = npcManager;
    }

    /* =========================
       MENU PRINCIPAL
       ========================= */

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE_MAIN);

        ItemStack filler = makeItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        // Bouton VENDRE
        inv.setItem(SLOT_SELL, makeItem(
                Material.COD,
                "§a💰 Vendre des ressources",
                List.of(
                        "§7Vendez vos ressources marines",
                        "§7et recevez des coins.",
                        "",
                        "§eCliquez pour voir les prix"
                )
        ));

        // Bouton ACHETER (placeholder)
        inv.setItem(SLOT_BUY, makeItem(
                Material.CYAN_STAINED_GLASS_PANE,
                "§6🛒 Acheter des ressources",
                List.of(
                        "§7Achetez des ressources marines",
                        "§7avec vos coins.",
                        "",
                        "§8(Disponible prochainement)"
                )
        ));

        // Bouton SUIVI
        boolean following = npcManager.isFollowing(player, CityNPC.JACKSPARROW);
        inv.setItem(SLOT_FOLLOW, makeItem(
                following ? Material.REDSTONE : Material.LIME_DYE,
                following ? "§c⛔ Arrêter de suivre" : "§a👣 Demander de suivre",
                List.of(
                        following ? "§7Jack arrêtera de vous suivre."
                                : "§7Jack vous suivra.",
                        "",
                        "§eCliquez pour " + (following ? "arrêter" : "activer")
                )
        ));

        player.openInventory(inv);
    }

    /* =========================
       SOUS-MENU VENDRE
       ========================= */

    public void openSell(Player player) {
        Map<Material, Integer> prices = config.getPrices();
        int size = Math.max(18, (int) Math.ceil((prices.size() + 1) / 9.0) * 9);
        Inventory inv = Bukkit.createInventory(null, size, GUI_TITLE_SELL);

        ItemStack filler = makeItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        int slot = 0;
        for (Map.Entry<Material, Integer> entry : prices.entrySet()) {
            if (slot == SLOT_BACK) slot++;

            Material mat    = entry.getKey();
            int price       = entry.getValue();
            int playerCount = countMaterial(player, mat);
            int fullStacks  = playerCount / 64;
            int totalValue  = fullStacks * price;

            List<String> lore = new ArrayList<>();
            lore.add("§7Prix : §6" + price + " coins §7/ stack");
            lore.add("");
            lore.add("§7Vos stocks : §f" + playerCount + " §7(" + fullStacks + " stacks)");
            lore.add("§7Valeur totale : §6" + totalValue + " coins");
            lore.add("");
            lore.add(fullStacks > 0
                    ? "§eCliquez pour vendre tous vos stacks"
                    : "§cVous n'avez pas de stack complet");

            inv.setItem(slot, makeItem(mat, "§f" + formatName(mat), lore));
            slot++;
        }

        inv.setItem(SLOT_BACK, makeItem(
                Material.ARROW,
                "§7← Retour",
                List.of("§7Retour au menu principal")
        ));

        player.openInventory(inv);
    }

    /* =========================
       SOUS-MENU ACHETER (placeholder)
       ========================= */

    public void openBuy(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE_BUY);

        ItemStack filler = makeItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        inv.setItem(4, makeItem(
                Material.BARRIER,
                "§cPas encore disponible",
                List.of("§7Cette fonctionnalité sera", "§7disponible prochainement.")
        ));

        inv.setItem(SLOT_BACK, makeItem(
                Material.ARROW,
                "§7← Retour",
                List.of("§7Retour au menu principal")
        ));

        player.openInventory(inv);
    }

    /* =========================
       LOGIQUE DE VENTE
       ========================= */

    public int sellMaterial(Player player, Material mat) {
        int price      = config.getPrice(mat);
        int stacksSold = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != mat) continue;
            if (item.getAmount() < 64) continue;
            player.getInventory().remove(item);
            stacksSold++;
        }

        if (stacksSold == 0) return -1;
        return stacksSold * price;
    }

    /* =========================
       HELPERS
       ========================= */

    private int countMaterial(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) count += item.getAmount();
        }
        return count;
    }

    private String formatName(Material mat) {
        StringBuilder sb = new StringBuilder();
        for (String word : mat.name().split("_")) {
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
        }
        return sb.toString().trim();
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