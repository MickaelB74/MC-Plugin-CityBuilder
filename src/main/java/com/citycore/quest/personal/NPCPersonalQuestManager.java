package com.citycore.quest.personal;

import com.citycore.npc.CityNPC;
import com.citycore.util.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
            db.getConnection().createStatement().execute("""
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
        return getState(playerUUID, npc, questId) == 1;
    }

    public boolean isCompleted(UUID playerUUID, CityNPC npc, String questId) {
        return getState(playerUUID, npc, questId) == 2;
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
        if (!isActive(playerUUID, npc, quest.id())) return false;

        int current = getProgress(playerUUID, npc, quest.id());
        int newVal  = Math.min(current + 1, quest.targetAmount());
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
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return done;
    }
}