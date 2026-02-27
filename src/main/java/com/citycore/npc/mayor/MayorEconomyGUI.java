package com.citycore.npc.mayor;

import com.citycore.city.City;
import com.citycore.city.CityManager;
import com.citycore.quest.city.CityQuestManager;
import com.citycore.util.ChatInputManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MayorEconomyGUI {

    public static final String GUI_TITLE = ChatColor.GOLD + "Économie de la ville";

    public static final int SLOT_DEPOSIT = 3;
    public static final int SLOT_BACK    = 8;

    private final CityManager cityManager;
    private final Economy     economy;
    private final CityQuestManager cityQuestManager;

    public MayorEconomyGUI(CityManager cityManager, Economy economy, CityQuestManager cityQuestManager) {
        this.cityManager = cityManager;
        this.economy     = economy;
        this.cityQuestManager = cityQuestManager;
    }

    public void open(Player player) {
        int cityCoins   = cityManager.getCityCoins();
        int playerCoins = (int) economy.getBalance(player);

        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE);

        ItemStack filler = makeItem(Material.YELLOW_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        // Bouton infos caisse
        inv.setItem(0, makeItem(
                Material.GOLD_BLOCK,
                "§6💰 Caisse de la ville",
                List.of(
                        "§7Solde actuel : §6" + cityCoins + " coins",
                        "",
                        "§7Votre argent : §e" + playerCoins + " coins"
                )
        ));

        // Bouton dépôt
        inv.setItem(SLOT_DEPOSIT, makeItem(
                Material.EMERALD,
                "§a⬆ Déposer des coins",
                List.of(
                        "§7Transférez vos coins",
                        "§7dans la caisse de la ville.",
                        "",
                        "§7Votre solde : §e" + playerCoins + " coins",
                        "",
                        "§eCliquez pour saisir le montant"
                )
        ));

        // Bouton retour
        inv.setItem(SLOT_BACK, makeItem(
                Material.ARROW,
                "§7◀ Retour",
                List.of("§7Retourner au menu principal")
        ));

        player.openInventory(inv);
    }

    /**
     * @return false si retour demandé
     */
    public boolean handleClick(Player player, int slot) {
        if (slot == SLOT_BACK) return false;

        if (slot == SLOT_DEPOSIT) {
            int playerCoins = (int) economy.getBalance(player);
            ChatInputManager.prompt(
                    player,
                    "§6⬆ §7Combien de coins voulez-vous §edéposer §7dans la caisse ?",
                    input -> {
                        int amount;
                        try {
                            amount = Integer.parseInt(input);
                        } catch (NumberFormatException e) {
                            player.sendMessage("§c❌ Valeur invalide : §f" + input);
                            open(player);
                            return;
                        }
                        if (amount <= 0) {
                            player.sendMessage("§c❌ Le montant doit être positif.");
                            open(player);
                            return;
                        }
                        if (!economy.has(player, amount)) {
                            player.sendMessage("§c❌ Vous n'avez pas assez de coins.");
                            player.sendMessage("§7Votre solde : §e" + (int) economy.getBalance(player) + " coins");
                            open(player);
                            return;
                        }
                        economy.withdrawPlayer(player, amount);
                        cityManager.addCityCoins(amount);
                        cityQuestManager.onCoinsDeposited(amount);
                        player.sendMessage("§a✅ §f" + amount + " §acoins déposés dans la caisse de la ville.");
                        player.sendMessage("§7Nouveau solde ville : §6" + cityManager.getCityCoins() + " coins");
                        open(player);
                    }
            );
            return true;
        }
        return true;
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