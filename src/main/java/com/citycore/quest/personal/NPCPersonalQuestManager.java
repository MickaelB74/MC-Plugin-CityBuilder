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
 * Table BDD : npc_personal_quest_progress
 *   player_uuid TEXT
 *   npc_tag     TEXT
 *   quest_id    TEXT
 *   progress    INTEGER  (valeur actuelle)
 *   completed   INTEGER  (0 = en cours, 1 = complétée)
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
                    completed   INTEGER NOT NULL DEFAULT 0,
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

    public boolean isCompleted(UUID playerUUID, CityNPC npc, String questId) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT completed FROM npc_personal_quest_progress
                WHERE player_uuid = ? AND npc_tag = ? AND quest_id = ?
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setString(3, questId);
            ResultSet rs = ps.executeQuery();
            boolean done = rs.next() && rs.getInt("completed") == 1;
            ps.close();
            return done;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* =========================
       PROGRESSION
       ========================= */

    /**
     * Incrémente la progression d'une quête perso.
     * @return true si la quête est maintenant complétée
     */
    public boolean increment(UUID playerUUID, CityNPC npc, NPCPersonalQuest quest) {
        if (isCompleted(playerUUID, npc, quest.id())) return true;

        int current = getProgress(playerUUID, npc, quest.id());
        int newVal  = Math.min(current + 1, quest.targetAmount());
        boolean done = newVal >= quest.targetAmount();

        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO npc_personal_quest_progress
                    (player_uuid, npc_tag, quest_id, progress, completed)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, npc_tag, quest_id)
                DO UPDATE SET progress = excluded.progress, completed = excluded.completed
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setString(3, quest.id());
            ps.setInt(4, newVal);
            ps.setInt(5, done ? 1 : 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return done;
    }

    /* =========================
       VALIDATION (récompense déjà donnée — marque comme réclamée)
       On considère completed=1 comme "validée et récompensée".
       Si on veut distinguer "finie mais pas encore réclamée",
       on peut ajouter un état claimed=2 plus tard.
       ========================= */

    public void markCompleted(UUID playerUUID, CityNPC npc, String questId) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO npc_personal_quest_progress
                    (player_uuid, npc_tag, quest_id, progress, completed)
                VALUES (?, ?, ?, 1, 1)
                ON CONFLICT(player_uuid, npc_tag, quest_id)
                DO UPDATE SET completed = 1
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
}