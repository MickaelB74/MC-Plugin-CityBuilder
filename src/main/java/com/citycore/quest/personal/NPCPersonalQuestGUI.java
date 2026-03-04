package com.citycore.quest.personal;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI listant toutes les quêtes personnelles disponibles pour un NPC,
 * avec la progression individuelle du joueur et un bouton retour.
 */
public class NPCPersonalQuestGUI {

    public static final String TITLE_PREFIX = "§5✦ Quêtes perso — ";

    private final CityNPC                npc;
    private final NPCDataManager         dataManager;
    private final NPCPersonalQuestManager questManager;

    public NPCPersonalQuestGUI(CityNPC npc,
                               NPCDataManager dataManager,
                               NPCPersonalQuestManager questManager) {
        this.npc          = npc;
        this.dataManager  = dataManager;
        this.questManager = questManager;
    }

    public static String title(CityNPC npc) {
        return TITLE_PREFIX + org.bukkit.ChatColor.stripColor(npc.displayName);
    }

    /* =========================
       OUVERTURE
       ========================= */

    public void open(Player player) {
        int npcLevel = dataManager.getLevel(npc);
        List<NPCPersonalQuest> quests = npc.getPersonalQuestsForLevel(npcLevel);

        // Taille : 9 slots par rangée, minimum 2 rangées (18), max 54
        int rows = Math.max(2, (int) Math.ceil((quests.size() + 1) / 9.0) + 1);
        rows = Math.min(rows, 6);
        int size = rows * 9;

        Inventory inv = Bukkit.createInventory(null, size, title(npc));

        // ── Filler ──────────────────────────────────────────────
        ItemStack filler = makeItem(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        // ── Quêtes ───────────────────────────────────────────────
        UUID uuid = player.getUniqueId();
        for (int i = 0; i < Math.min(quests.size(), size - 9); i++) {
            inv.setItem(i, buildQuestItem(uuid, quests.get(i)));
        }

        // ── Bouton retour (dernière rangée, slot 0) ──────────────
        inv.setItem(size - 9, makeItem(Material.ARROW, "§7← Retour", List.of()));

        player.openInventory(inv);
    }

    /* =========================
       CLIC — géré dans QuestListener
       ========================= */

    /**
     * Appelle la validation d'une quête complétée et donne la récompense.
     * Retourne true si la récompense a été donnée.
     */
    public boolean tryValidate(Player player, int slot,
                               net.milkbowl.vault.economy.Economy economy) {
        int npcLevel = dataManager.getLevel(npc);
        List<NPCPersonalQuest> quests = npc.getPersonalQuestsForLevel(npcLevel);
        if (slot < 0 || slot >= quests.size()) return false;

        NPCPersonalQuest quest = quests.get(slot);
        UUID uuid = player.getUniqueId();

        if (!questManager.isCompleted(uuid, npc, quest.id())) {
            player.sendMessage("§c❌ Quête non complétée !");
            return false;
        }

        // Déjà validé ? (progress=1 completed=1 mais récompense déjà donnée)
        // On pourrait ajouter un état "claimed" mais pour l'instant on empêche juste
        // le clic sur les items terminés en ne mettant pas de lore "cliquez"
        // → le bouton est disabled (icône verte, pas de lore d'action)
        player.sendMessage("§a🎉 Quête §f" + quest.displayName()
                + " §adéjà complétée !");
        return false;
    }

    /* =========================
       CONSTRUCTION ITEM
       ========================= */

    private ItemStack buildQuestItem(UUID uuid, NPCPersonalQuest quest) {
        boolean completed = questManager.isCompleted(uuid, npc, quest.id());
        int progress      = questManager.getProgress(uuid, npc, quest.id());
        int target        = quest.targetAmount();

        List<String> lore = new ArrayList<>();
        lore.add("§7" + quest.description());
        lore.add("");

        if (completed) {
            lore.add("§a✔ Complétée !");
        } else if (progress > 0) {
            lore.add(buildProgressBar(progress, target)
                    + " §f" + progress + "§7/§f" + target);
            lore.add("");
            lore.add("§e⏳ En cours...");
        } else {
            if (target > 1) {
                lore.add("§7Objectif : §f0§7/§f" + target);
                lore.add("");
            }
            lore.add("§7Explorez le monde pour progresser !");
        }

        lore.add("");
        lore.add("§7Récompense : §6" + quest.reward() + " coins");

        Material icon   = completed ? Material.LIME_DYE : quest.icon();
        String   name   = completed
                ? "§a✔ " + quest.displayName()
                : (progress > 0 ? "§e⏳ " : "§f") + quest.displayName();

        return makeItem(icon, name, lore);
    }

    /* =========================
       HELPERS
       ========================= */

    private String buildProgressBar(int current, int target) {
        int len    = 8;
        int filled = Math.min((int) ((double) current / target * len), len);
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < len; i++) bar.append(i < filled ? "§a█" : "§8░");
        bar.append("§7]");
        return bar.toString();
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

    public CityNPC getNpc() { return npc; }
}