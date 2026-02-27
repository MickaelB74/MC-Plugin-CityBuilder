package com.citycore.npc.mayor;

import com.citycore.quest.city.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MayorQuestGUI {

    private final CityQuestManager    questManager;
    private final FindNpcQuestManager findNpcQuestManager;

    public MayorQuestGUI(CityQuestManager questManager,
                         FindNpcQuestManager findNpcQuestManager) {
        this.questManager        = questManager;
        this.findNpcQuestManager = findNpcQuestManager;
    }

    public static String title(CityTier tier) {
        return ChatColor.DARK_PURPLE + "Quêtes — "
                + ChatColor.stripColor(tier.tierName)
                + " (Niv." + tier.level + ")";
    }

    public void open(Player player) {
        CityTier tier = CityTier.fromLevel(questManager.getCityLevel());
        questManager.syncCurrentTier();

        List<CityQuest> quests = tier.quests;
        Inventory inv = Bukkit.createInventory(null, 36, title(tier));

        // ── Filler barre du bas ──────────────────────────────────
        ItemStack filler = makeItem(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 27; i < 36; i++) inv.setItem(i, filler);

        // ── Quêtes ───────────────────────────────────────────────
        for (int i = 0; i < Math.min(quests.size(), 27); i++) {
            CityQuest quest  = quests.get(i);
            boolean done     = questManager.isCompleted(quest.id());
            int current      = questManager.computeCurrentValue(quest);
            int target       = quest.targetValue();

            List<String> lore = new ArrayList<>();
            lore.add("§7" + quest.description());
            lore.add("");

            Material icon;
            String name;

            // ── FIND_NPC — traitement spécial ────────────────────
            if (quest.type() == CityQuestType.FIND_NPC) {
                boolean active = findNpcQuestManager.isQuestActive(quest.id());

                if (done) {
                    lore.add("§a✔ Complétée !");
                    icon = Material.LIME_DYE;
                    name = "§a✔ " + quest.displayName();
                } else if (active) {
                    lore.add("§e⏳ En cours...");
                    lore.add("§7Cliquez pour récupérer la boussole.");
                    icon = Material.COMPASS;
                    name = "§e⏳ " + quest.displayName();
                } else {
                    lore.add("§7Cliquez pour démarrer la quête.");
                    icon = quest.icon();
                    name = "§e" + quest.displayName();
                }

                // ── Quêtes standards ─────────────────────────────────
            } else if (done) {
                lore.add("§a✔ Complétée !");
                icon = Material.LIME_DYE;
                name = "§a✔ " + quest.displayName();
            } else {
                lore.add("§7Progression : §f" + current + " §7/ §f" + target);
                lore.add(progressBar(current, target));
                icon = quest.icon();
                name = "§e" + quest.displayName();
            }

            inv.setItem(i, makeItem(icon, name, lore));
        }

        // ── Bouton améliorer ville ───────────────────────────────
        boolean tierComplete = questManager.isTierComplete(tier);
        boolean isMaxTier    = tier.isMaxTier();

        if (isMaxTier) {
            inv.setItem(27 + 4, makeItem(Material.BEACON,
                    "§6⭐ Niveau maximum atteint !",
                    List.of("§7Votre ville est au sommet de sa gloire.")));
        } else if (tierComplete) {
            inv.setItem(27 + 4, makeItem(Material.NETHER_STAR,
                    "§a⬆ Améliorer la ville",
                    List.of(
                            "§7Toutes les quêtes sont complètes !",
                            "",
                            "§aCliquez pour passer au niveau suivant",
                            "§7→ " + tier.next().tierName
                    )));
        } else {
            long remaining = tier.quests.stream()
                    .filter(q -> q.type() != CityQuestType.FIND_NPC)
                    .filter(q -> !questManager.isCompleted(q.id()))
                    .count();
            inv.setItem(27 + 4, makeItem(Material.BARRIER,
                    "§c⬆ Améliorer la ville",
                    List.of(
                            "§cComplétez toutes les quêtes d'abord.",
                            "",
                            "§7Quêtes restantes : §c" + remaining
                    )));
        }

        // ── Bouton retour ────────────────────────────────────────
        inv.setItem(27 + 8, makeItem(Material.ARROW,
                "§7◀ Retour", List.of("§7Retourner au menu du Maire")));

        player.openInventory(inv);
    }

    public boolean handleClick(Player player, int slot) {
        CityTier tier = CityTier.fromLevel(questManager.getCityLevel());

        // ── Retour ───────────────────────────────────────────────
        if (slot == 27 + 8) return false;

        // ── Améliorer la ville ───────────────────────────────────
        if (slot == 27 + 4) {
            if (tier.isMaxTier()) return true;
            if (!questManager.isTierComplete(tier)) {
                player.sendMessage("§c❌ Complétez d'abord toutes les quêtes !");
                return true;
            }
            questManager.upgradeCityLevel();
            CityTier next = tier.next();
            player.sendMessage("§a🎉 La ville est passée au niveau §e"
                    + next.tierName + " §a(niveau §e" + next.level + "§a) !");
            open(player);
            return true;
        }

        // ── Clic sur une quête ───────────────────────────────────
        if (slot < 27 && slot < tier.quests.size()) {
            CityQuest quest = tier.quests.get(slot);

            if (quest.type() == CityQuestType.FIND_NPC
                    && !questManager.isCompleted(quest.id())) {

                if (findNpcQuestManager.isQuestActive(quest.id())) {
                    // Quête active — redonne l'item et recrée la BossBar
                    findNpcQuestManager.giveQuestItem(player, quest);
                    findNpcQuestManager.createBossBar(player, quest);
                    player.sendMessage("§7🗺 Quête déjà en cours : §e" + quest.displayName());
                    player.closeInventory();
                } else {
                    // Déclenche la quête
                    player.closeInventory();
                    findNpcQuestManager.triggerQuest(quest, player);
                }
                return true;
            }
        }

        return true;
    }

    /* =========================
       HELPERS
       ========================= */

    private String progressBar(int current, int target) {
        int filled = (int) ((double) current / target * 10);
        filled = Math.min(filled, 10);
        return "§a" + "▬".repeat(filled) + "§8" + "▬".repeat(10 - filled);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
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