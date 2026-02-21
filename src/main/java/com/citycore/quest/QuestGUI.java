package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class QuestGUI {

    public static String titleQuests(CityNPC npc) {
        return "§8[" + npc.displayName + "§8] §dQuêtes";
    }

    public static final int SLOT_MAIN    = 2;
    public static final int SLOT_SPECIAL = 6;
    public static final int SLOT_BACK    = 8;

    private final CityNPC        npcType;
    private final QuestConfig    questConfig;
    private final QuestManager   questManager;
    private final NPCDataManager dataManager;

    public QuestGUI(CityNPC npcType, QuestConfig questConfig,
                    QuestManager questManager, NPCDataManager dataManager) {
        this.npcType      = npcType;
        this.questConfig  = questConfig;
        this.questManager = questManager;
        this.dataManager  = dataManager;
    }

    /* =========================
       MENU QUÊTES
       ========================= */

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, titleQuests(npcType));
        UUID uuid    = player.getUniqueId();
        int npcLevel = dataManager.getLevel(npcType);

        ItemStack filler = makeItem(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        // ── Quête Principale ─────────────────────────────────────
        QuestDefinition activeMain = questManager.getActiveQuest(uuid, npcType, false);
        QuestDefinition displayMain = activeMain != null
                ? activeMain
                : questConfig.generateMain(npcLevel);
        inv.setItem(SLOT_MAIN, buildQuestItem(player, displayMain, Material.BOOK,
                activeMain != null));

        // ── Quête Spéciale ───────────────────────────────────────
        QuestDefinition activeSpecial = questManager.getActiveQuest(uuid, npcType, true);
        QuestDefinition displaySpecial = activeSpecial != null
                ? activeSpecial
                : questConfig.generateSpecial(npcLevel);
        inv.setItem(SLOT_SPECIAL, buildQuestItem(player, displaySpecial,
                Material.NETHER_STAR, activeSpecial != null));

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    /* =========================
       CONSTRUCTION ITEM QUÊTE
       ========================= */

    public ItemStack buildQuestItem(Player player, QuestDefinition quest,
                                    Material icon, boolean isActive) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> progress = isActive
                ? questManager.getProgress(uuid, npcType, quest.isSpecial())
                : new HashMap<>();

        boolean allDone = isActive && questManager.isAllCompleted(progress, quest);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + quest.description());
        lore.add("");

        // Objectifs
        for (QuestObjective obj : quest.objectives()) {
            int current  = progress.getOrDefault(obj.id(), 0);
            boolean done = current >= obj.amount();
            String label = obj.isMaterialObjective()
                    ? formatName(obj.material().name())
                    : "Tuer " + formatName(obj.entity().name());

            if (isActive) {
                lore.add((done ? "§a✔ " : "§7• ") + "§f" + label
                        + " §7: §f" + current + "§7/§f" + obj.amount());
            } else {
                lore.add("§7• §f" + label + " §7: §f0§7/§f" + obj.amount());
            }
        }

        lore.add("");
        lore.add("§7Récompense : §6" + quest.reward().coins() + " coins");
        lore.add("");

        if (!isActive) {
            lore.add("§a▶ Cliquez pour accepter");
        } else if (allDone) {
            lore.add("§a★ Cliquez pour valider et récupérer !");
        } else {
            lore.add("§e⏳ En cours...");
        }

        String title = quest.isSpecial()
                ? "§d✦ Quête Spéciale" : "§9📖 Quête Principale";
        return makeItem(icon, title, lore);
    }

    /* =========================
       GETTERS
       ========================= */

    public CityNPC getNpcType()           { return npcType; }
    public QuestConfig getQuestConfig()   { return questConfig; }
    public QuestManager getQuestManager() { return questManager; }

    /* =========================
       HELPERS
       ========================= */

    private String formatName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("_")) {
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase()).append(" ");
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