package com.citycore.command;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import com.citycore.quest.QuestHUD;
import com.citycore.quest.personal.StoryJournal;
import com.citycore.ui.UiItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Journal de quêtes classique ouvert par "/city quests" (joueurs sans le pack « Valmont »).
 *
 * Suivi : une tête de skin pour chaque NPC ayant une quête active ; cliquer sur la tête bascule
 * le suivi (visible ou masqué dans le panneau). L'état est celui de {@link QuestHUD} (partagé avec
 * le menu illustré, gardé dans les préférences du joueur). Dernière rangée : affichage du suivi
 * (visible, accroché à la liste des joueurs, masqué) à gauche, livre des récits au milieu.
 *
 * Récits : les personnages dans l'ordre de la trame (survivants, puis légendes) ; colorant gris =
 * pas encore à Valmont ; un clic sur un personnage installé ouvre l'écran de ses récits.
 *
 * Titres identifiables : GUI_TITLE, STORIES_TITLE
 * Gestion des clics : handleClick(player, item), handleStoriesClick(player, item)
 */
public class CityQuestSelectionGUI {

    public static final String GUI_TITLE     = "§6Quêtes — Suivi";
    public static final String STORIES_TITLE = "§5✦ Récits des habitants";

    // Clés PDC : NPC concerné par une tête, action d'un bouton
    private static final String PDC_KEY    = "cityquest_npc_tag";
    private static final String PDC_ACTION = "cityquest_action";

    private final QuestHUD   questHUD;
    private final NPCManager npcManager;
    private final JavaPlugin plugin;
    private StoryJournal     journal;

    public CityQuestSelectionGUI(QuestHUD questHUD, NPCManager npcManager, JavaPlugin plugin) {
        this.questHUD   = questHUD;
        this.npcManager = npcManager;
        this.plugin     = plugin;
    }

    /** Journal des récits (onglet « Récits »). */
    public void setJournal(StoryJournal journal) {
        this.journal = journal;
    }

    /* =========================
       OUVERTURE — SUIVI
       ========================= */

    public void open(Player player) {
        // NPCs ayant au moins une quête active pour ce joueur
        List<CityNPC> activeNpcs = questHUD.npcsWithActiveQuest(player);

        // Rangées des têtes (1 à 5) + une rangée de boutons
        int rows = Math.min(5, Math.max(1, (int) Math.ceil(activeNpcs.size() / 9.0)));
        int size = (rows + 1) * 9;

        Inventory inv = Bukkit.createInventory(null, size, GUI_TITLE);
        NamespacedKey pdcKey = new NamespacedKey(plugin, PDC_KEY);

        for (int i = 0; i < Math.min(rows * 9, activeNpcs.size()); i++) {
            CityNPC npc     = activeNpcs.get(i);
            boolean tracked = questHUD.isNpcTracked(player.getUniqueId(), npc);
            inv.setItem(i, makeSkinHead(npc, tracked, pdcKey));
        }

        if (activeNpcs.isEmpty()) {
            ItemStack noQuest = new ItemStack(Material.BARRIER);
            ItemMeta  meta    = noQuest.getItemMeta();
            meta.setDisplayName("§cAucune quête active");
            meta.setLore(List.of("§7Acceptez des quêtes auprès des NPCs."));
            noQuest.setItemMeta(meta);
            inv.setItem(4, noQuest);
        }

        // Affichage du suivi : clic gauche = mode suivant, clic droit = précédent
        QuestHUD.Mode mode = questHUD.mode(player);
        Material modeIcon = switch (mode) {
            case VISIBLE  -> Material.LIME_DYE;
            case ACCROCHE -> Material.LIGHT_BLUE_DYE;
            case MASQUE   -> Material.GRAY_DYE;
        };
        inv.setItem(size - 9, actionItem(modeIcon, QuestHUD.modeTitle(mode),
                QuestHUD.modeLore(mode, false, true), "mode"));

        if (journal != null) {
            inv.setItem(size - 5, actionItem(Material.WRITABLE_BOOK, "§5✦ Récits des habitants",
                    List.of("§7Où vous en êtes dans les récits", "§7de chaque personnage.", "", "§e▶ Cliquez pour ouvrir"),
                    "recits"));
        }

        player.openInventory(inv);
    }

    /* =========================
       OUVERTURE — RÉCITS
       ========================= */

    public void openStories(Player player) {
        if (journal == null) return;
        Inventory inv = Bukkit.createInventory(null, 54, STORIES_TITLE);
        NamespacedKey pdcKey = new NamespacedKey(plugin, PDC_KEY);

        List<StoryJournal.Entry> entries = journal.entries(player.getUniqueId());
        int survivors = (int) entries.stream().filter(e -> !e.legend()).count();
        int legendStart = (survivors + 8) / 9 * 9; // les légendes commencent une rangée plus bas
        int s = 0, l = 0, done = 0, total = 0, toAccept = 0, active = 0;
        for (StoryJournal.Entry e : entries) {
            int slot = e.legend() ? legendStart + l++ : s++;
            if (slot < 45) inv.setItem(slot, storyItem(e, pdcKey));
            done += e.done();
            total += e.total();
            if (e.clickable()) {
                toAccept += e.toAccept();
                active += e.active();
            }
        }

        inv.setItem(45, actionItem(Material.ARROW, "§7← Suivi des quêtes", List.of(), "suivi"));
        inv.setItem(49, plainItem(Material.BOOK, "§5✦ Récits accomplis : §f" + done + " §7/ §f" + total, List.of(
                "§e" + toAccept + " §7à accepter · §6" + active + " §7en cours", "",
                "§7Survivants en haut, légendes ensuite.",
                "§7Colorant gris : pas encore à Valmont.",
                "§7Cliquez sur un personnage installé",
                "§7pour ouvrir ses récits.")));
        player.openInventory(inv);
    }

    /** Tête du personnage (colorant gris s'il n'est pas encore à Valmont). */
    private ItemStack storyItem(StoryJournal.Entry e, NamespacedKey pdcKey) {
        String title = StoryJournal.title(e);
        List<String> lore = StoryJournal.lore(e);
        if (e.status() == StoryJournal.Status.LOCKED) return plainItem(Material.GRAY_DYE, title, lore);
        ItemStack head = UiItems.head(npcManager.resolveSkin(e.npc()), e.npc().tag, title, lore);
        if (e.clickable()) {
            ItemMeta meta = head.getItemMeta();
            meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, e.npc().tag);
            head.setItemMeta(meta);
        }
        return head;
    }

    /* =========================
       GESTION DES CLICS
       ========================= */

    /**
     * Suivi : bascule le suivi d'un NPC, change l'affichage du suivi, ou ouvre les récits.
     * @param rightClick clic droit (mode d'affichage précédent)
     * @return true si le clic a été traité (annuler l'event + ne pas fermer)
     */
    public boolean handleClick(Player player, ItemStack clicked, boolean rightClick) {
        if (clicked == null || !clicked.hasItemMeta()) return false;
        ItemMeta meta = clicked.getItemMeta();

        if ("recits".equals(action(meta))) {
            openStories(player);
            return true;
        }
        if ("mode".equals(action(meta))) {
            questHUD.announce(player, questHUD.cycle(player, !rightClick));
            open(player);
            return true;
        }

        NamespacedKey pdcKey = new NamespacedKey(plugin, PDC_KEY);
        if (!meta.getPersistentDataContainer().has(pdcKey, PersistentDataType.STRING)) return false;

        String npcTag = meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
        CityNPC npc   = CityNPC.fromTag(npcTag);
        if (npc == null) return false;

        UUID uuid = player.getUniqueId();
        boolean tracked = questHUD.isNpcTracked(uuid, npc);
        questHUD.setNpcTracked(uuid, npc, !tracked);
        player.sendMessage(tracked
                ? "§7📋 Quêtes de §e" + npc.displayName.replaceAll("§.", "") + " §7masquées du suivi."
                : "§a📋 Quêtes de §e" + npc.displayName.replaceAll("§.", "") + " §aaffichées dans le suivi.");

        // Rafraîchit le GUI immédiatement
        open(player);
        return true;
    }

    /** Récits : retour au suivi, ou récits d'un personnage installé (retour vers ce menu). */
    public boolean handleStoriesClick(Player player, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta() || journal == null) return false;
        ItemMeta meta = clicked.getItemMeta();
        if ("suivi".equals(action(meta))) {
            open(player);
            return true;
        }
        NamespacedKey pdcKey = new NamespacedKey(plugin, PDC_KEY);
        CityNPC npc = CityNPC.fromTag(meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING));
        return npc != null && journal.open(player, npc, () -> openStories(player));
    }

    /* =========================
       HELPERS — TÊTES, BOUTONS
       ========================= */

    private ItemStack makeSkinHead(CityNPC npc, boolean tracked, NamespacedKey pdcKey) {
        String displayName = npc.displayName.replaceAll("§.", "");
        List<String> lore = new ArrayList<>();
        lore.add("§7" + npc.function);
        lore.add("");
        if (tracked) {
            lore.add("§a● Suivi §7— quête affichée dans le suivi");
            lore.add("§7Cliquez pour §cmasquer§7.");
        } else {
            lore.add("§8○ Non suivi §7— quête absente du suivi");
            lore.add("§7Cliquez pour §aafficher§7.");
        }

        // Texture du skin (valeur codée ou config.yml npc-skins), sinon tête par défaut
        ItemStack head = UiItems.head(npcManager.resolveSkin(npc), npc.tag,
                (tracked ? "§a✔ " : "§7✘ ") + "§f" + displayName, lore);
        ItemMeta meta = head.getItemMeta();
        // PDC pour identifier le NPC au clic
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, npc.tag);
        head.setItemMeta(meta);
        return head;
    }

    /** Bouton : objet portant une action (PDC). */
    private ItemStack actionItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = plainItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack plainItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private String action(ItemMeta meta) {
        return meta.getPersistentDataContainer().get(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING);
    }

    /* =========================
       API publique
       ========================= */

    /** Vérifie si les quêtes d'un NPC sont suivies pour un joueur. */
    public boolean isTracked(UUID uuid, CityNPC npc) {
        return questHUD.isNpcTracked(uuid, npc);
    }
}
