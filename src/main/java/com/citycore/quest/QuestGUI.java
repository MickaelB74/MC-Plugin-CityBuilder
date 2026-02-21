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
        inv.setItem(SLOT_MAIN, resolveQuestItem(player, uuid, npcLevel, false,
                Material.BOOK));

        // ── Quête Spéciale ───────────────────────────────────────
        inv.setItem(SLOT_SPECIAL, resolveQuestItem(player, uuid, npcLevel, true,
                Material.NETHER_STAR));

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    private ItemStack resolveQuestItem(Player player, UUID uuid, int npcLevel,
                                       boolean isSpecial, Material icon) {
        // 1. Quête active (en cours ou prête à valider)
        QuestDefinition active = questManager.getActiveQuest(uuid, npcType, isSpecial);
        if (active != null) {
            return buildQuestItem(player, active, icon, true);
        }

        // 2. Quête pending (générée mais pas encore acceptée)
        QuestDefinition pending = questManager.getPendingQuest(uuid, npcType, isSpecial);
        if (pending != null) {
            return buildQuestItem(player, pending, icon, false);
        }

        // 3. Aucune quête — génère et stocke en pending
        QuestDefinition generated = isSpecial
                ? questConfig.generateSpecial(npcLevel)
                : questConfig.generateMain(npcLevel);
        questManager.setPendingQuest(uuid, npcType, generated);
        return buildQuestItem(player, generated, icon, false);
    }

    /* =========================
       CONSTRUCTION ITEM QUÊTE
       ========================= */

    public ItemStack buildQuestItem(Player player, QuestDefinition quest,
                                    Material icon, boolean isActive) {
        UUID uuid = player.getUniqueId();
        boolean isReady = isActive
                && questManager.isReadyToValidate(uuid, npcType, quest.isSpecial());
        Map<String, Integer> progress = isActive
                ? questManager.getProgress(uuid, npcType, quest.isSpecial())
                : new HashMap<>();

        List<String> lore = new ArrayList<>();
        lore.add("§7" + quest.description());
        lore.add("");

        // ── Objectifs avec progression ───────────────────────────
        for (QuestObjective obj : quest.objectives()) {
            int current  = progress.getOrDefault(obj.id(), 0);
            int required = obj.amount();
            boolean done = current >= required;

            String label = obj.isMaterialObjective()
                    ? formatName(obj.material().name())
                    : "Tuer " + formatName(obj.entity().name());

            if (isActive) {
                // Barre de progression visuelle
                String bar = buildProgressBar(current, required);
                lore.add((done ? "§a✔ " : "§7• ") + "§f" + label);
                lore.add("  " + bar + " §f" + current + "§7/§f" + required);
            } else {
                lore.add("§7• §f" + label + " §7: §f0§7/§f" + required);
            }
        }

        lore.add("");
        lore.add("§7Récompense : §6" + quest.reward().coins() + " coins");
        lore.add("");

        // ── Indicateur d'action ──────────────────────────────────
        if (!isActive) {
            lore.add("§a▶ Cliquez pour accepter");
        } else if (isReady) {
            lore.add("§a★ Prêt ! Cliquez pour valider !");
        } else {
            lore.add("§e⏳ En cours — fermez votre inventaire");
            lore.add("§e   pour mettre à jour la progression");
        }

        String title = quest.isSpecial()
                ? "§d✦ Quête Spéciale" : "§9📖 Quête Principale";

        // Icône verte si prête, dorée si en cours, normale si pas commencée
        Material displayIcon = !isActive ? icon
                : isReady ? Material.EMERALD : Material.CLOCK;

        return makeItem(displayIcon, title, lore);
    }

    private String buildProgressBar(int current, int required) {
        int barLength = 8;
        int filled    = Math.min((int) ((double) current / required * barLength), barLength);
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? "§a█" : "§8░");
        }
        bar.append("§7]");
        return bar.toString();
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