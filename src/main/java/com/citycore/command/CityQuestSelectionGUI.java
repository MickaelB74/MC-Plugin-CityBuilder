package com.citycore.command;

import com.citycore.npc.CityNPC;
import com.citycore.quest.QuestGUI;
import com.citycore.quest.QuestHUD;
import com.citycore.quest.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * GUI ouvert par "/city quests".
 *
 * Affiche une tête de skin pour chaque NPC ayant une quête active.
 * Cliquer sur la tête bascule le suivi (visible ou masqué dans le scoreboard).
 *
 * Titre identifiable : GUI_TITLE
 * Gestion des clics : handleClick(player, slot)
 */
public class CityQuestSelectionGUI {

    public static final String GUI_TITLE = "§6Quêtes — Suivi";

    // Clé PDC stockée dans chaque tête pour identifier le NPC concerné
    private static final String PDC_KEY = "cityquest_npc_tag";

    private final QuestManager   questManager;
    private final QuestHUD       questHUD;
    private final List<QuestGUI> questGUIs;
    private final JavaPlugin     plugin;

    /**
     * Ensemble des NPC dont les quêtes sont MASQUÉES dans le scoreboard.
     * Par défaut tout est suivi (set vide = tout visible).
     */
    private final Map<UUID, Set<String>> hiddenNpcs = new HashMap<>();

    public CityQuestSelectionGUI(QuestManager questManager, QuestHUD questHUD,
                                 List<QuestGUI> questGUIs, JavaPlugin plugin) {
        this.questManager = questManager;
        this.questHUD     = questHUD;
        this.questGUIs    = questGUIs;
        this.plugin       = plugin;
    }

    /* =========================
       OUVERTURE
       ========================= */

    public void open(Player player) {
        // Détermine les NPCs ayant au moins une quête active pour ce joueur
        List<CityNPC> activeNpcs = new ArrayList<>();
        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            boolean hasMain    = questManager.getActiveQuest(player.getUniqueId(), npc, false) != null;
            boolean hasSpecial = questManager.getActiveQuest(player.getUniqueId(), npc, true)  != null;
            if (hasMain || hasSpecial) activeNpcs.add(npc);
        }

        // Calcule la taille de l'inventaire (ligne de 9, min 9)
        int rows = Math.max(1, (int) Math.ceil(activeNpcs.size() / 9.0));
        int size = rows * 9;

        Inventory inv = Bukkit.createInventory(null, size, GUI_TITLE);

        Set<String> hidden = hiddenNpcs.getOrDefault(player.getUniqueId(), Collections.emptySet());

        NamespacedKey pdcKey = new NamespacedKey(plugin, PDC_KEY);

        for (int i = 0; i < activeNpcs.size(); i++) {
            CityNPC npc     = activeNpcs.get(i);
            boolean tracked = !hidden.contains(npc.tag);

            ItemStack head = makeSkinHead(npc, tracked, pdcKey);
            inv.setItem(i, head);
        }

        if (activeNpcs.isEmpty()) {
            ItemStack noQuest = new ItemStack(Material.BARRIER);
            ItemMeta  meta    = noQuest.getItemMeta();
            meta.setDisplayName("§cAucune quête active");
            meta.setLore(List.of("§7Acceptez des quêtes auprès des NPCs."));
            noQuest.setItemMeta(meta);
            inv.setItem(4, noQuest);
        }

        player.openInventory(inv);
    }

    /* =========================
       GESTION DES CLICS
       ========================= */

    /**
     * @return true si le clic a été traité (annuler l'event + ne pas fermer)
     */
    public boolean handleClick(Player player, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return false;

        NamespacedKey pdcKey = new NamespacedKey(plugin, PDC_KEY);
        ItemMeta meta = clicked.getItemMeta();
        if (!meta.getPersistentDataContainer().has(pdcKey, PersistentDataType.STRING)) return false;

        String npcTag = meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
        CityNPC npc   = CityNPC.fromTag(npcTag);
        if (npc == null) return false;

        UUID uuid = player.getUniqueId();
        Set<String> hidden = hiddenNpcs.computeIfAbsent(uuid, k -> new HashSet<>());

        if (hidden.contains(npcTag)) {
            // Était masqué → on suit maintenant
            hidden.remove(npcTag);
            player.sendMessage("§a📋 Quêtes de §e"
                    + npc.displayName.replaceAll("§.", "") + " §aaffichées dans le scoreboard.");
            questHUD.setNpcTracked(uuid, npc, true);
        } else {
            // Était suivi → on masque
            hidden.add(npcTag);
            player.sendMessage("§7📋 Quêtes de §e"
                    + npc.displayName.replaceAll("§.", "") + " §7masquées du scoreboard.");
            questHUD.setNpcTracked(uuid, npc, false);
        }

        // Rafraîchit le GUI immédiatement
        open(player);
        return true;
    }

    /* =========================
       HELPERS — TÊTES
       ========================= */

    private ItemStack makeSkinHead(CityNPC npc, boolean tracked, NamespacedKey pdcKey) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skull = (SkullMeta) head.getItemMeta();

        // Applique la texture du skin du NPC
        if (npc.skinValue != null && !npc.skinValue.isEmpty()) {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), npc.tag);
                PlayerTextures textures = profile.getTextures();
                // La skinValue est encodée en base64 (JSON), on la décode pour extraire l'URL
                String decoded = new String(Base64.getDecoder().decode(npc.skinValue));
                // Format : {"textures":{"SKIN":{"url":"http://..."}}}
                int urlStart = decoded.indexOf("\"url\":\"") + 7;
                int urlEnd   = decoded.indexOf("\"", urlStart);
                if (urlStart > 6 && urlEnd > urlStart) {
                    URL skinUrl = new URL(decoded.substring(urlStart, urlEnd));
                    textures.setSkin(skinUrl);
                    profile.setTextures(textures);
                    skull.setOwnerProfile(profile);
                }
            } catch (MalformedURLException | IllegalArgumentException ignored) {
                // Fallback si décodage impossible : tête de joueur par défaut
            }
        }

        // Nom + lore
        String displayName = npc.displayName.replaceAll("§.", "");
        skull.setDisplayName((tracked ? "§a✔ " : "§7✘ ") + "§f" + displayName);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + npc.function);
        lore.add("");
        if (tracked) {
            lore.add("§a● Suivi §7— quête visible dans le scoreboard");
            lore.add("§7Cliquez pour §cmasquer§7.");
        } else {
            lore.add("§8○ Non suivi §7— quête cachée du scoreboard");
            lore.add("§7Cliquez pour §aafficher§7.");
        }
        skull.setLore(lore);
        skull.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        // PDC pour identifier le NPC au clic
        skull.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, npc.tag);

        head.setItemMeta(skull);
        return head;
    }

    /* =========================
       API publique
       ========================= */

    /** Vérifie si les quêtes d'un NPC sont suivies pour un joueur. */
    public boolean isTracked(UUID uuid, CityNPC npc) {
        return !hiddenNpcs.getOrDefault(uuid, Collections.emptySet()).contains(npc.tag);
    }
}