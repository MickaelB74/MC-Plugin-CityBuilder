package com.citycore.building;

import com.citycore.util.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BuildingManager {

    private final DatabaseManager db;

    public BuildingManager(DatabaseManager db) {
        this.db = db;
    }

    public void createBuilding(String name, String world,
                               int x1, int z1, int x2, int z2) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO buildings (name, world, x1, z1, x2, z2)
                VALUES (?, ?, ?, ?, ?, ?)
            """);
            ps.setString(1, name);
            ps.setString(2, world);
            ps.setInt(3, Math.min(x1, x2));
            ps.setInt(4, Math.min(z1, z2));
            ps.setInt(5, Math.max(x1, x2));
            ps.setInt(6, Math.max(z1, z2));
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Building> getAllBuildings() {
        List<Building> list = new ArrayList<>();
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT * FROM buildings ORDER BY name");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Building(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getInt("x1"), rs.getInt("z1"),
                        rs.getInt("x2"), rs.getInt("z2"),
                        rs.getString("npc_tag")
                ));
            }
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean nameExists(String name) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT 1 FROM buildings WHERE LOWER(name) = LOWER(?)");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            ps.close();
            return exists;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean removeByName(String name) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "DELETE FROM buildings WHERE LOWER(name) = LOWER(?)");
            ps.setString(1, name);
            int affected = ps.executeUpdate();
            ps.close();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void removeAll() {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "DELETE FROM buildings");
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retourne le bâtiment à une position X/Z donnée, null si aucun.
     */
    public Building getBuildingAt(String world, int x, int z) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            SELECT * FROM buildings
            WHERE world = ? AND ? BETWEEN x1 AND x2 AND ? BETWEEN z1 AND z2
            LIMIT 1
        """);
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, z);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { ps.close(); return null; }
            Building b = new Building(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("world"),
                    rs.getInt("x1"), rs.getInt("z1"),
                    rs.getInt("x2"), rs.getInt("z2"),
                    rs.getString("npc_tag")
            );
            ps.close();
            return b;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Assigne un NPC à un bâtiment par son id.
     */
    public boolean assignNPC(int buildingId, String npcTag) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            UPDATE buildings SET npc_tag = ? WHERE id = ?
        """);
            ps.setString(1, npcTag);
            ps.setInt(2, buildingId);
            int affected = ps.executeUpdate();
            ps.close();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retire le NPC assigné à un bâtiment.
     */
    public boolean unassignNPC(int buildingId) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE buildings SET npc_tag = NULL WHERE id = ?");
            ps.setInt(1, buildingId);
            int affected = ps.executeUpdate();
            ps.close();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void deleteBuilding(int id) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "DELETE FROM buildings WHERE id = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}