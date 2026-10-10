package com.citycore.quest.personal;

import com.citycore.npc.CityNPC;
import com.citycore.util.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gère la progression individuelle des quêtes personnelles de NPC.
 *
 * États en BDD (colonne `state`) :
 *   0 = non commencée / inconnue
 *   1 = active (acceptée par le joueur)
 *   2 = complétée
 *
 * La progression n'est incrémentée que si la quête est active (state=1).
 *
 * Table : npc_personal_quest_progress
 *   player_uuid TEXT
 *   npc_tag     TEXT
 *   quest_id    TEXT
 *   progress    INTEGER
 *   state       INTEGER  (0 | 1 | 2)
 *   PRIMARY KEY (player_uuid, npc_tag, quest_id)
 */
public class NPCPersonalQuestManager {

    private final DatabaseManager db;

    public NPCPersonalQuestManager(DatabaseManager db) {
        this.db = db;
        createTable();
    }

    /* =========================
       INIT TABLE
       ========================= */

    private void createTable() {
        try {
            db.execute("""
                CREATE TABLE IF NOT EXISTS npc_personal_quest_progress (
                    player_uuid TEXT NOT NULL,
                    npc_tag     TEXT NOT NULL,
                    quest_id    TEXT NOT NULL,
                    progress    INTEGER NOT NULL DEFAULT 0,
                    state       INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, npc_tag, quest_id)
                )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       LECTURE
       ========================= */

    public int getProgress(UUID playerUUID, CityNPC npc, String questId) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT progress FROM npc_personal_quest_progress
                WHERE player_uuid = ? AND npc_tag = ? AND quest_id = ?
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setString(3, questId);
            ResultSet rs = ps.executeQuery();
            int val = rs.next() ? rs.getInt("progress") : 0;
            ps.close();
            return val;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int getState(UUID playerUUID, CityNPC npc, String questId) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT state FROM npc_personal_quest_progress
                WHERE player_uuid = ? AND npc_tag = ? AND quest_id = ?
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setString(3, questId);
            ResultSet rs = ps.executeQuery();
            int val = rs.next() ? rs.getInt("state") : 0;
            ps.close();
            return val;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public boolean isActive(UUID playerUUID, CityNPC npc, String questId) {
        return getActiveEntries(playerUUID).contains(cacheKey(npc, questId));
    }

    /* =========================
       CACHE DES QUÊTES ACTIVES
       Les validators MOVE sont évalués à chaque déplacement : on évite
       d'interroger la base pour chaque PNJ et chaque quête.
       ========================= */

    private final Map<UUID, Set<String>> activeCache = new HashMap<>();

    /** Entrées "npc_tag|quest_id" des quêtes actives d'un joueur. */
    public Set<String> getActiveEntries(UUID playerUUID) {
        return activeCache.computeIfAbsent(playerUUID, this::loadActive);
    }

    /** Libère le cache d'un joueur (déconnexion). */
    public void evict(UUID playerUUID) {
        activeCache.remove(playerUUID);
    }

    public static String cacheKey(CityNPC npc, String questId) {
        return npc.tag + "|" + questId;
    }

    private Set<String> loadActive(UUID playerUUID) {
        Set<String> set = new HashSet<>();
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT npc_tag, quest_id FROM npc_personal_quest_progress
                WHERE player_uuid = ? AND state = 1
            """);
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) set.add(rs.getString("npc_tag") + "|" + rs.getString("quest_id"));
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return set;
    }

    public boolean isCompleted(UUID playerUUID, CityNPC npc, String questId) {
        return getState(playerUUID, npc, questId) == 2;
    }

    /**
     * Tous les récits d'un joueur en une requête (journal des récits) :
     * "npc_tag|quest_id" → {progression, état (0 | 1 | 2)}.
     */
    public Map<String, int[]> snapshot(UUID playerUUID) {
        Map<String, int[]> map = new HashMap<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT npc_tag, quest_id, progress, state FROM npc_personal_quest_progress
                WHERE player_uuid = ?
            """)) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString("npc_tag") + "|" + rs.getString("quest_id"),
                        new int[]{rs.getInt("progress"), rs.getInt("state")});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /* =========================
       ACTIVATION (acceptation)
       ========================= */

    /**
     * Marque la quête comme active.
     * Appelé quand le joueur l'accepte dans le GUI.
     */
    public void accept(UUID playerUUID, CityNPC npc, String questId) {
        if (isCompleted(playerUUID, npc, questId)) return; // déjà finie, pas de recul
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO npc_personal_quest_progress
                    (player_uuid, npc_tag, quest_id, progress, state)
                VALUES (?, ?, ?, 0, 1)
                ON CONFLICT(player_uuid, npc_tag, quest_id)
                DO UPDATE SET state = 1
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setString(3, questId);
            ps.executeUpdate();
            ps.close();
            getActiveEntries(playerUUID).add(cacheKey(npc, questId));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       PROGRESSION
       ========================= */

    /**
     * Incrémente la progression d'une quête perso.
     * Ne fait rien si la quête n'est pas active (state != 1).
     *
     * @return true si la quête est maintenant complétée
     */
    public boolean increment(UUID playerUUID, CityNPC npc, NPCPersonalQuest quest) {
        return increment(playerUUID, npc, quest, 1);
    }

    /** Incrémente de {@code amount} (ex : plusieurs fabrications d'un coup). */
    public boolean increment(UUID playerUUID, CityNPC npc, NPCPersonalQuest quest, int amount) {
        if (!isActive(playerUUID, npc, quest.id())) return false;

        int current = getProgress(playerUUID, npc, quest.id());
        int newVal  = Math.min(current + Math.max(1, amount), quest.targetAmount());
        boolean done = newVal >= quest.targetAmount();

        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                UPDATE npc_personal_quest_progress
                SET progress = ?, state = ?
                WHERE player_uuid = ? AND npc_tag = ? AND quest_id = ?
            """);
            ps.setInt(1, newVal);
            ps.setInt(2, done ? 2 : 1);
            ps.setString(3, playerUUID.toString());
            ps.setString(4, npc.tag);
            ps.setString(5, quest.id());
            ps.executeUpdate();
            ps.close();
            if (done) getActiveEntries(playerUUID).remove(cacheKey(npc, quest.id()));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return done;
    }
}