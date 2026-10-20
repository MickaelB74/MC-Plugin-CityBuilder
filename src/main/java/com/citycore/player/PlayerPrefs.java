package com.citycore.player;

import com.citycore.util.DatabaseManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Préférences de chaque joueur : affichage du suivi des quêtes (visible, accroché, masqué),
 * habitants dont les quêtes ne sont pas suivies, position du panneau des quêtes, style des menus.
 *
 * Elles sont rangées dans la base SQLite (table {@code player_prefs}) et écrites aussitôt : elles ne
 * dépendent pas de la sauvegarde des données du joueur par le serveur (arrêt brutal, serveur hybride
 * qui ne garderait pas les données des plugins…). Lues une fois par connexion, puis gardées en
 * mémoire ; oubliées à la déconnexion.
 *
 * Reprise des anciens réglages : jusqu'ici rangés dans les données du joueur
 * (PersistentDataContainer), ils sont recopiés ici à sa première connexion puis effacés de là
 * (un joueur qui avait masqué le panneau des quêtes le retrouve masqué).
 */
public final class PlayerPrefs implements Listener {

    /** Affichage du suivi des quêtes : « visible », « accroche » ou « masque » (voir QuestHUD.Mode). */
    public static final String QUEST_HUD_MODE  = "quest_hud_mode";
    /** Habitants dont les quêtes ne sont pas suivies (tags séparés par des virgules). */
    public static final String QUEST_UNTRACKED = "quest_untracked";
    /** Position du panneau des quêtes (0 = bord de l'écran, absent = 0). */
    public static final String QUEST_PANEL_POS = "quest_panel_pos";
    /** Menus des habitants classiques plutôt qu'illustrés (/city ui), « 1 » ou absent. */
    public static final String UI_CLASSIC      = "ui_classic";
    /** Réponse du joueur au pack des menus : « accepte », « refuse » ou absent (jamais répondu). */
    public static final String UI_PACK         = "ui_pack";

    /** Valeur de l'ancien drapeau « panneau masqué » une fois reprise (QuestHUD.Mode.MASQUE). */
    private static final String LEGACY_HIDDEN_MODE = "masque";

    private final JavaPlugin      plugin;
    private final DatabaseManager db;
    /** Préférences des joueurs connectés (préférence → valeur). */
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();

    // Anciennes clés des données du joueur (avant la table player_prefs)
    private final NamespacedKey legacyHidden;
    private final NamespacedKey legacyUntracked;
    private final NamespacedKey legacyPosition;
    private final NamespacedKey legacyClassic;

    public PlayerPrefs(JavaPlugin plugin, DatabaseManager db) {
        this.plugin          = plugin;
        this.db              = db;
        this.legacyHidden    = new NamespacedKey(plugin, "quest_hud_hidden");
        this.legacyUntracked = new NamespacedKey(plugin, "quest_hud_untracked");
        this.legacyPosition  = new NamespacedKey(plugin, "quest_panel_pos");
        this.legacyClassic   = new NamespacedKey(plugin, "ui_classic");
    }

    /* =========================
       LECTURE / ÉCRITURE
       ========================= */

    /** Valeur d'une préférence, ou null si le joueur ne l'a jamais réglée. */
    public String get(Player player, String pref) {
        return prefs(player).get(pref);
    }

    /** Préférence entière ({@code def} si absente ou illisible). */
    public int getInt(Player player, String pref, int def) {
        String raw = get(player, pref);
        if (raw == null) return def;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Préférence oui / non (absente = non). */
    public boolean flag(Player player, String pref) {
        return "1".equals(get(player, pref));
    }

    public void setFlag(Player player, String pref, boolean on) {
        set(player, pref, on ? "1" : null);
    }

    /** Règle une préférence (null ou vide = retour à la valeur par défaut) ; écrite aussitôt en base. */
    public void set(Player player, String pref, String value) {
        Map<String, String> prefs = prefs(player);
        UUID uuid = player.getUniqueId();
        if (value == null || value.isBlank()) {
            if (prefs.remove(pref) != null) delete(uuid, pref);
        } else if (!value.equals(prefs.put(pref, value))) {
            write(uuid, pref, value);
        }
    }

    /** Préférences du joueur : en mémoire, sinon lues en base (et anciens réglages repris). */
    private Map<String, String> prefs(Player player) {
        Map<String, String> prefs = cache.get(player.getUniqueId());
        if (prefs != null) return prefs;
        prefs = load(player);
        // Joueur déjà parti (tâche différée) : rien n'est gardé en mémoire
        if (player.isOnline()) cache.put(player.getUniqueId(), prefs);
        return prefs;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // MONITOR : les autres écouteurs de la déconnexion ont déjà lu ce qu'il leur fallait
        cache.remove(event.getPlayer().getUniqueId());
    }

    /* =========================
       BASE (table player_prefs, créée par DatabaseManager)
       ========================= */

    private Map<String, String> load(Player player) {
        Map<String, String> prefs = new ConcurrentHashMap<>();
        UUID uuid = player.getUniqueId();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT pref, value FROM player_prefs WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) prefs.put(rs.getString("pref"), rs.getString("value"));
            }
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().warning("⚠ Préférences de " + player.getName() + " illisibles : " + e.getMessage());
            return prefs; // valeurs par défaut ; les anciens réglages restent en place pour la prochaine fois
        }
        migrateLegacy(player, prefs);
        return prefs;
    }

    private boolean write(UUID uuid, String pref, String value) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO player_prefs (uuid, pref, value) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, pref);
            ps.setString(3, value);
            ps.executeUpdate();
            return true;
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().warning("⚠ Préférence « " + pref + " » non enregistrée : " + e.getMessage());
            return false;
        }
    }

    private void delete(UUID uuid, String pref) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM player_prefs WHERE uuid = ? AND pref = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, pref);
            ps.executeUpdate();
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().warning("⚠ Préférence « " + pref + " » non effacée : " + e.getMessage());
        }
    }

    /* =========================
       REPRISE DES ANCIENS RÉGLAGES (données du joueur)
       ========================= */

    /**
     * Recopie les anciens réglages rangés dans les données du joueur, sauf ceux déjà présents en
     * base, puis les efface de là. Un réglage dont l'écriture échoue reste en place (nouvel essai à
     * la prochaine connexion).
     */
    private void migrateLegacy(Player player, Map<String, String> prefs) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        UUID uuid = player.getUniqueId();
        Map<String, String> found = new HashMap<>();

        Byte hidden = read(pdc, legacyHidden, PersistentDataType.BYTE);
        if (hidden != null && hidden == 1) found.put(QUEST_HUD_MODE, LEGACY_HIDDEN_MODE);
        String untracked = read(pdc, legacyUntracked, PersistentDataType.STRING);
        if (untracked != null && !untracked.isBlank()) found.put(QUEST_UNTRACKED, untracked);
        Integer position = read(pdc, legacyPosition, PersistentDataType.INTEGER);
        if (position != null && position != 0) found.put(QUEST_PANEL_POS, String.valueOf(position));
        if (pdc.has(legacyClassic)) found.put(UI_CLASSIC, "1");

        boolean ok = true;
        for (Map.Entry<String, String> e : found.entrySet()) {
            if (prefs.containsKey(e.getKey())) continue; // déjà réglé depuis : la base l'emporte
            if (write(uuid, e.getKey(), e.getValue())) prefs.put(e.getKey(), e.getValue());
            else ok = false;
        }
        if (!ok) return;
        for (NamespacedKey key : new NamespacedKey[]{legacyHidden, legacyUntracked, legacyPosition, legacyClassic}) {
            if (pdc.has(key)) pdc.remove(key);
        }
        if (!found.isEmpty()) {
            plugin.getLogger().info("Préférences de " + player.getName() + " reprises des données du joueur : "
                    + String.join(", ", found.keySet()) + ".");
        }
    }

    /** Valeur d'une ancienne clé, ou null (absente ou d'un autre type). */
    private static <P, C> C read(PersistentDataContainer pdc, NamespacedKey key, PersistentDataType<P, C> type) {
        try {
            return pdc.get(key, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
