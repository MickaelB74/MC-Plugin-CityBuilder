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
                               int x1, int z1, int x2, int z2,
                               Double npcX, Double npcY, Double npcZ) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            INSERT INTO buildings (name, world, x1, z1, x2, z2, npc_x, npc_y, npc_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """);
            ps.setString(1, name);
            ps.setString(2, world);
            ps.setInt(3, Math.min(x1, x2));
            ps.setInt(4, Math.min(z1, z2));
            ps.setInt(5, Math.max(x1, x2));
            ps.setInt(6, Math.max(z1, z2));
            if (npcX != null) {
                ps.setDouble(7, npcX);
                ps.setDouble(8, npcY);
                ps.setDouble(9, npcZ);
            } else {
                ps.setNull(7, java.sql.Types.REAL);
                ps.setNull(8, java.sql.Types.REAL);
                ps.setNull(9, java.sql.Types.REAL);
            }
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Building buildFromResultSet(ResultSet rs) throws SQLException {
        double npcX = rs.getDouble("npc_x");
        double npcY = rs.getDouble("npc_y");
        double npcZ = rs.getDouble("npc_z");
        return new Building(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("world"),
                rs.getInt("x1"), rs.getInt("z1"),
                rs.getInt("x2"), rs.getInt("z2"),
                rs.getString("npc_tag"),
                rs.wasNull() ? null : npcX,
                rs.wasNull() ? null : npcY,
                rs.wasNull() ? null : npcZ
        );
    }

    public List<Building> getAllBuildings() {
        List<Building> list = new ArrayList<>();
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT * FROM buildings ORDER BY name");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(buildFromResultSet(rs));
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

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
            Building b = buildFromResultSet(rs);
            ps.close();
            return b;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Building getAssignedBuilding(String npcTag) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT * FROM buildings WHERE npc_tag = ?");
            ps.setString(1, npcTag);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { ps.close(); return null; }
            Building b = buildFromResultSet(rs);
            ps.close();
            return b;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
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

    /**
     * Vérifie si une zone chevauche un bâtiment existant.
     */
    public boolean overlapsExisting(String world, int x1, int z1, int x2, int z2) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            SELECT 1 FROM buildings
            WHERE world = ?
            AND x1 <= ? AND x2 >= ?
            AND z1 <= ? AND z2 >= ?
        """);
            ps.setString(1, world);
            ps.setInt(2, Math.max(x1, x2));
            ps.setInt(3, Math.min(x1, x2));
            ps.setInt(4, Math.max(z1, z2));
            ps.setInt(5, Math.min(z1, z2));
            ResultSet rs = ps.executeQuery();
            boolean overlaps = rs.next();
            ps.close();
            return overlaps;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Vérifie si un NPC est déjà assigné à un bâtiment.
     */
    public boolean isNPCAlreadyAssigned(String npcTag) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT 1 FROM buildings WHERE npc_tag = ?");
            ps.setString(1, npcTag);
            ResultSet rs = ps.executeQuery();
            boolean assigned = rs.next();
            ps.close();
            return assigned;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Vérifie si un bâtiment a déjà un NPC assigné.
     */
    public boolean buildingHasNPC(int buildingId) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT npc_tag FROM buildings WHERE id = ? AND npc_tag IS NOT NULL");
            ps.setInt(1, buildingId);
            ResultSet rs = ps.executeQuery();
            boolean has = rs.next();
            ps.close();
            return has;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void unassignNPCByTag(String npcTag) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE buildings SET npc_tag = NULL WHERE npc_tag = ?");
            ps.setString(1, npcTag);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}