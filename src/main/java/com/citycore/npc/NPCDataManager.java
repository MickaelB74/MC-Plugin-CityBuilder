package com.citycore.npc;

import com.citycore.util.DatabaseManager;
import org.bukkit.Material;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class NPCDataManager {

    private final DatabaseManager db;

    public NPCDataManager(DatabaseManager db) {
        this.db = db;
    }

    /* =========================
       XP & NIVEAU
       ========================= */

    public int getXP(CityNPC npc) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT xp FROM npc_data WHERE npc_tag = ?");
            ps.setString(1, npc.tag);
            ResultSet rs = ps.executeQuery();
            int xp = rs.next() ? rs.getInt("xp") : 0;
            ps.close();
            return xp;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getLevel(CityNPC npc) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT level FROM npc_data WHERE npc_tag = ?");
            ps.setString(1, npc.tag);
            ResultSet rs = ps.executeQuery();
            int level = rs.next() ? rs.getInt("level") : 1;
            ps.close();
            return level;
        } catch (SQLException e) {
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * Ajoute de l'XP au NPC et met à jour le niveau si nécessaire.
     * @return true si le NPC a monté de niveau
     */
    public boolean addXP(CityNPC npc, int amount, Map<Integer, Integer> thresholds) {
        try {
            // Initialise si pas encore en BDD
            initNPCData(npc);

            int currentXP    = getXP(npc);
            int currentLevel = getLevel(npc);
            int newXP        = currentXP + amount;
            int newLevel     = calculateLevel(newXP, thresholds);

            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE npc_data SET xp = ?, level = ? WHERE npc_tag = ?");
            ps.setInt(1, newXP);
            ps.setInt(2, newLevel);
            ps.setString(3, npc.tag);
            ps.executeUpdate();
            ps.close();

            return newLevel > currentLevel;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private int calculateLevel(int xp, Map<Integer, Integer> thresholds) {
        int level = 1;
        for (Map.Entry<Integer, Integer> entry : thresholds.entrySet()) {
            if (xp >= entry.getValue()) level = entry.getKey();
        }
        return Math.min(level, 4);
    }

    private void initNPCData(CityNPC npc) throws SQLException {
        PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT OR IGNORE INTO npc_data (npc_tag, xp, level) VALUES (?, 0, 1)");
        ps.setString(1, npc.tag);
        ps.executeUpdate();
        ps.close();
    }

    /* =========================
       INVENTAIRE NPC
       ========================= */

    /**
     * Ajoute des items à l'inventaire du NPC (items vendus par les joueurs).
     */
    public void addToInventory(CityNPC npc, Material mat, int amount) {
        try {
            initNPCData(npc);
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO npc_inventory (npc_tag, material, amount)
                VALUES (?, ?, ?)
                ON CONFLICT(npc_tag, material)
                DO UPDATE SET amount = amount + excluded.amount
            """);
            ps.setString(1, npc.tag);
            ps.setString(2, mat.name());
            ps.setInt(3, amount);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retire des items de l'inventaire du NPC (rachat ville).
     * @return true si le retrait a réussi
     */
    public boolean removeFromInventory(CityNPC npc, Material mat, int amount) {
        int current = getInventoryAmount(npc, mat);
        if (current < amount) return false;

        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE npc_inventory SET amount = amount - ? WHERE npc_tag = ? AND material = ?");
            ps.setInt(1, amount);
            ps.setString(2, npc.tag);
            ps.setString(3, mat.name());
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getInventoryAmount(CityNPC npc, Material mat) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT amount FROM npc_inventory WHERE npc_tag = ? AND material = ?");
            ps.setString(1, npc.tag);
            ps.setString(2, mat.name());
            ResultSet rs = ps.executeQuery();
            int amount = rs.next() ? rs.getInt("amount") : 0;
            ps.close();
            return amount;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Retourne tout l'inventaire du NPC.
     */
    public Map<Material, Integer> getInventory(CityNPC npc) {
        Map<Material, Integer> inventory = new LinkedHashMap<>();
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT material, amount FROM npc_inventory WHERE npc_tag = ? AND amount > 0");
            ps.setString(1, npc.tag);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                try {
                    Material mat = Material.valueOf(rs.getString("material"));
                    inventory.put(mat, rs.getInt("amount"));
                } catch (IllegalArgumentException ignored) {}
            }
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return inventory;
    }

    public void setLevel(CityNPC npc, int level, Map<Integer, Integer> thresholds) {
        try {
            initNPCData(npc);
            int targetLevel = Math.max(1, Math.min(4, level));
            // XP = seuil du niveau cible (ou 0 pour niveau 1)
            int targetXP = targetLevel == 1 ? 0 : thresholds.getOrDefault(targetLevel, 0);

            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE npc_data SET level = ?, xp = ? WHERE npc_tag = ?");
            ps.setInt(1, targetLevel);
            ps.setInt(2, targetXP);
            ps.setString(3, npc.tag);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}