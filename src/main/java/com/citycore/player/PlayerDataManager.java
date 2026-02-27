package com.citycore.player;

import com.citycore.npc.CityNPC;
import com.citycore.util.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerDataManager {

    // ✅ Formule globale : XP nécessaire pour passer au niveau suivant
    // xpRequired(level) = 100 * level^2
    public static int xpForNextLevel(int level) {
        return 100 * level * level;
    }

    private final DatabaseManager db;

    public PlayerDataManager(DatabaseManager db) {
        this.db = db;
    }

    /* =========================
       XP & LEVEL
       ========================= */

    public int getXP(UUID uuid) {
        return getInt(uuid, "xp");
    }

    public int getLevel(UUID uuid) {
        return getInt(uuid, "level");
    }

    /**
     * Ajoute de l'XP au joueur et gère les montées de niveau.
     * Retourne le nombre de niveaux gagnés (0 si aucun).
     */
    public int addXP(UUID uuid, int amount) {
        ensurePlayer(uuid);
        int xp    = getXP(uuid) + amount;
        int level = getLevel(uuid);
        int levelsGained = 0;

        // ✅ Montée de niveau en cascade si besoin
        while (xp >= xpForNextLevel(level)) {
            xp -= xpForNextLevel(level);
            level++;
            levelsGained++;
        }

        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                UPDATE players SET xp = ?, level = ? WHERE uuid = ?
            """);
            ps.setInt(1, xp);
            ps.setInt(2, level);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return levelsGained;
    }

    public void setLevelAndXP(UUID uuid, int level, int xp) {
        ensurePlayer(uuid);
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE players SET level = ?, xp = ? WHERE uuid = ?");
            ps.setInt(1, Math.max(1, level));
            ps.setInt(2, Math.max(0, xp));
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       JOB
       ========================= */

    public CityNPC getJob(UUID uuid) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT job FROM players WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (!rs.next() || rs.getString("job") == null) { ps.close(); return null; }
            String tag = rs.getString("job");
            ps.close();
            for (CityNPC npc : CityNPC.values()) {
                if (npc.tag.equals(tag)) return npc;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setJob(UUID uuid, CityNPC npc) {
        ensurePlayer(uuid);
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE players SET job = ? WHERE uuid = ?");
            ps.setString(1, npc != null ? npc.tag : null);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void clearJob(UUID uuid) {
        setJob(uuid, null);
    }

    /* =========================
       HELPERS
       ========================= */

    public void ensurePlayer(UUID uuid) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT OR IGNORE INTO players (uuid, xp, level, job)
                VALUES (?, 0, 1, NULL)
            """);
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getInt(UUID uuid, String column) {
        ensurePlayer(uuid);
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT " + column + " FROM players WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            int val = rs.next() ? rs.getInt(column) : 0;
            ps.close();
            return val;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}