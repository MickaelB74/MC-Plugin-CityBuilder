package com.citycore.npc;

import com.citycore.util.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class IntroductionManager {

    private final DatabaseManager db;

    public IntroductionManager(DatabaseManager db) {
        this.db = db;
    }

    /**
     * Vérifie si le joueur a déjà vu l'intro de ce NPC.
     */
    public boolean hasSeenIntro(UUID playerUUID, CityNPC npc) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT 1 FROM npc_introductions
                WHERE player_uuid = ? AND npc_tag = ?
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ResultSet rs = ps.executeQuery();
            boolean seen = rs.next();
            ps.close();
            return seen;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Marque l'intro comme vue pour ce joueur + NPC.
     */
    public void markIntroSeen(UUID playerUUID, CityNPC npc) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT OR IGNORE INTO npc_introductions (player_uuid, npc_tag)
                VALUES (?, ?)
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}