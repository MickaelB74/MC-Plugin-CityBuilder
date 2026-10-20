package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.quest.personal.NPCPersonalQuestGUI;
import com.citycore.quest.personal.PersonalQuestDefinition;
import com.citycore.quest.personal.PersonalQuestRegistry;
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

    public static final int SLOT_MAIN     = 2;
    public static final int SLOT_SPECIAL  = 6;
    public static final int SLOT_PERSONAL = 4; // slot central — uniquement si quêtes perso
    public static final int SLOT_BACK     = 8;
    public static final int SLOT_JOURNAL  = 0; // journal de quêtes : suivi et affichage du suivi

    private final CityNPC             npcType;
    private final QuestConfig         questConfig;
    private final QuestManager        questManager;
    private final NPCDataManager      dataManager;
    private final NPCPersonalQuestGUI personalQuestGUI; // null si le NPC n'en a pas
    /** Écrans illustrés « Valmont » (null = menu classique uniquement). */
    private com.citycore.ui.screens.QuestScreens themed;

    public void setThemed(com.citycore.ui.screens.QuestScreens themed) {
        this.themed = themed;
    }

    /** Ouvre le journal de quêtes, onglet « Suivi » (null = pas d'accès depuis ce menu). */
    private java.util.function.Consumer<Player> journal;

    public void setJournal(java.util.function.Consumer<Player> journal) {
        this.journal = journal;
    }

    public boolean hasJournal() {
        return journal != null;
    }

    /** Journal de quêtes : habitants suivis, position du panneau, affichage du suivi. */
    public void openJournal(Player player) {
        if (journal != null) journal.accept(player);
    }

    public QuestGUI(CityNPC npcType, QuestConfig questConfig,
                    QuestManager questManager, NPCDataManager dataManager,
                    NPCPersonalQuestGUI personalQuestGUI) {
        this.npcType          = npcType;
        this.questConfig      = questConfig;
        this.questManager     = questManager;
        this.dataManager      = dataManager;
        this.personalQuestGUI = personalQuestGUI;
    }

    /* =========================
       MENU QUÊTES
       ========================= */

    public void open(Player player) {
        if (themed != null && themed.openQuests(player, this)) return; // joueur équipé du pack
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

        // ── Quêtes Personnelles (slot central, si disponibles) ───
        if (hasPersonalQuests(npcLevel)) {
            inv.setItem(SLOT_PERSONAL, buildPersonalQuestButton(npcLevel));
        }

        // ── Journal de quêtes (suivi, affichage visible / accroché / masqué) ──
        if (journal != null) {
            inv.setItem(SLOT_JOURNAL, makeItem(Material.MAP, "§6📋 Journal de quêtes", List.of(
                    "§7Quêtes suivies et affichage du suivi :",
                    "§7visible, accroché à la liste des",
                    "§7joueurs (Tab) ou masqué.",
                    "",
                    "§e▶ Cliquez pour ouvrir")));
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    public boolean hasPersonalQuests(int npcLevel) {
        return personalQuestGUI != null
                && !npcType.getPersonalQuestsForLevel(npcLevel).isEmpty();
    }

    private ItemStack buildPersonalQuestButton(int npcLevel) {
        int count = npcType.getPersonalQuestsForLevel(npcLevel).size();
        int total = npcType.getAllPersonalQuests().size();
        List<String> lore = new ArrayList<>();
        lore.add("§7" + count + " récit" + (count > 1 ? "s" : "") + " débloqué" + (count > 1 ? "s" : "")
                + " sur " + total);
        lore.add("");
        lore.add("§a▶ Cliquez pour consulter");
        return makeItem(Material.COMPASS, "§5✦ Quêtes Personnelles", lore);
    }

    /* =========================
       ÉTAT D'UNE COMMANDE (partagé avec les écrans illustrés)
       ========================= */

    /**
     * État d'une quête de PNJ pour un joueur.
     * @param quest      quête active ou proposée (null : en repos ou aucune)
     * @param cooldownMs délai avant la prochaine quête (0 sinon)
     */
    public record Card(QuestDefinition quest, boolean active, boolean ready,
                       Map<String, Integer> progress, long cooldownMs) {}

    /** Quête active, sinon proposée (générée au besoin, comme le menu classique), sinon repos. */
    public Card card(Player player, boolean isSpecial) {
        UUID uuid = player.getUniqueId();
        QuestDefinition active = questManager.getActiveQuest(uuid, npcType, isSpecial);
        if (active != null) {
            return new Card(active, true, questManager.isReadyToValidate(uuid, npcType, isSpecial),
                    questManager.getProgress(uuid, npcType, isSpecial), 0);
        }
        QuestDefinition pending = questManager.getPendingQuest(uuid, npcType, isSpecial);
        if (pending != null) return new Card(pending, false, false, Map.of(), 0);
        long remaining = questManager.getCooldownRemaining(uuid, npcType, isSpecial);
        if (remaining > 0) return new Card(null, false, false, Map.of(), remaining);
        int npcLevel = dataManager.getLevel(npcType);
        QuestDefinition generated = isSpecial
                ? questConfig.generateSpecial(npcLevel)
                : questConfig.generateMain(npcLevel);
        if (generated == null) return new Card(null, false, false, Map.of(), 0);
        questManager.setPendingQuest(uuid, npcType, generated);
        return new Card(generated, false, false, Map.of(), 0);
    }

    /* =========================
       RÉSOLUTION ITEM QUÊTE STD
       ========================= */

    private ItemStack resolveQuestItem(Player player, UUID uuid, int npcLevel,
                                       boolean isSpecial, Material icon) {
        QuestDefinition active = questManager.getActiveQuest(uuid, npcType, isSpecial);
        if (active != null) return buildQuestItem(player, active, icon, true);

        QuestDefinition pending = questManager.getPendingQuest(uuid, npcType, isSpecial);
        if (pending != null) return buildQuestItem(player, pending, icon, false);

        // Délai entre deux quêtes du même type
        long remaining = questManager.getCooldownRemaining(uuid, npcType, isSpecial);
        if (remaining > 0) {
            long minutes = Math.max(1, (remaining + 59_999) / 60_000);
            return makeItem(Material.CLOCK,
                    isSpecial ? "§d✦ Quête Spéciale" : "§9📖 Quête Principale",
                    List.of("§7" + npcType.displayName + " §7prépare",
                            "§7sa prochaine quête.",
                            "",
                            "§eDisponible dans " + minutes + " min"));
        }

        QuestDefinition generated = isSpecial
                ? questConfig.generateSpecial(npcLevel)
                : questConfig.generateMain(npcLevel);
        if (generated == null) {
            return makeItem(Material.BARRIER,
                    isSpecial ? "§d✦ Quête Spéciale" : "§9📖 Quête Principale",
                    List.of("§7Aucune quête de ce type", "§7pour le moment."));
        }
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

        for (QuestObjective obj : quest.objectives()) {
            int current  = progress.getOrDefault(obj.id(), 0);
            int required = obj.amount();
            boolean done = current >= required;
            String label = buildObjectiveLabel(obj);

            if (isActive) {
                String bar = buildProgressBar(current, required);
                lore.add((done ? "§a✔ " : "§7• ") + label);
                lore.add("  " + bar + " §f" + current + "§7/§f" + required);
            } else {
                lore.add("§7• " + label + " §7: §f0§7/§f" + required);
            }
        }

        lore.add("");
        lore.add("§7Récompense : §6" + quest.reward().coins() + " coins");
        if (quest.objectives().stream().anyMatch(QuestObjective::isMaterialObjective)) {
            lore.add("§8Les items achetés à un PNJ ne comptent pas.");
        }
        lore.add("");

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

        Material displayIcon = !isActive ? icon
                : isReady ? Material.EMERALD : Material.CLOCK;

        return makeItem(displayIcon, title, lore);
    }

    /* =========================
       LABEL D'OBJECTIF
       ========================= */

    public String buildObjectiveLabel(QuestObjective obj) {
        // Libellé de l'entrée de quête (famille « Laine (toutes couleurs) », objet ou créature de mod)
        if (obj.isMaterialObjective()) {
            return "§f" + QuestConfig.label(npcType, obj);
        } else if (obj.isEntityObjective()) {
            return "§fTuer " + QuestConfig.label(npcType, obj);
        } else if (obj.isBiomeObjective()) {
            return "§7Explorer §f" + formatName(obj.biome().name());
        } else if (obj.isPersonalObjective()) {
            PersonalQuestDefinition def = PersonalQuestRegistry.get(obj.personalId());
            if (def != null) return def.displayName();
            return "§7Quête : §f" + obj.personalId();
        }
        return "§cObjectif inconnu";
    }

    /* =========================
       BARRE DE PROGRESSION
       ========================= */

    private String buildProgressBar(int current, int required) {
        int barLength = 8;
        int filled    = Math.min((int) ((double) current / required * barLength), barLength);
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < barLength; i++) bar.append(i < filled ? "§a█" : "§8░");
        bar.append("§7]");
        return bar.toString();
    }

    /* =========================
       GETTERS
       ========================= */

    public CityNPC getNpcType()                      { return npcType; }
    public QuestConfig getQuestConfig()              { return questConfig; }
    public QuestManager getQuestManager()            { return questManager; }
    public NPCPersonalQuestGUI getPersonalQuestGUI() { return personalQuestGUI; }

    /* =========================
       HELPERS
       ========================= */

    private String formatName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("_")) {
            if (word.isEmpty()) continue;
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