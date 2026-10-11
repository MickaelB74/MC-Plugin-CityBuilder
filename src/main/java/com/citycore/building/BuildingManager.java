package com.citycore.building;

import com.citycore.npc.CityNPC;
import com.citycore.util.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BuildingManager {

    private final DatabaseManager db;

    /** Appelé avec le tag de l'artisan dont le bâtiment vient d'être supprimé (voir NPCState.HOMELESS). */
    private java.util.function.Consumer<String> onNpcEvicted = tag -> { };

    public void setOnNpcEvicted(java.util.function.Consumer<String> onNpcEvicted) {
        this.onNpcEvicted = onNpcEvicted;
    }

    /** Prévient pour chaque artisan qui habitait un des bâtiments supprimés. */
    private void evict(List<Building> removed) {
        for (Building b : removed) if (b.npcTag() != null) onNpcEvicted.accept(b.npcTag());
    }

    /** Ordre des listes : par type (habitations, ateliers, mairie, divers), puis par nom. */
    public static final Comparator<Building> DISPLAY_ORDER = Comparator
            .comparingInt((Building b) -> b.type().ordinal())
            .thenComparing(Building::name, String.CASE_INSENSITIVE_ORDER);

    public BuildingManager(DatabaseManager db) {
        this.db = db;
    }

    /**
     * Enregistre un bâtiment. Le point du PNJ est facultatif (npcX null) ; la hauteur aussi
     * (yMin/yMax null : estimée au prochain démarrage).
     */
    public void createBuilding(String name, String world,
                               int x1, int z1, int x2, int z2,
                               Double npcX, Double npcY, Double npcZ,
                               Float npcYaw,
                               BuildingType type, String reservedFor,
                               Integer yMin, Integer yMax) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            INSERT INTO buildings (name, world, x1, z1, x2, z2,
                                   npc_x, npc_y, npc_z, npc_yaw,
                                   type, reserved_for, y_min, y_max)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                ps.setFloat(10, npcYaw != null ? npcYaw : 0f);
            } else {
                ps.setNull(7, java.sql.Types.REAL);
                ps.setNull(8, java.sql.Types.REAL);
                ps.setNull(9, java.sql.Types.REAL);
                ps.setNull(10, java.sql.Types.REAL);
            }
            BuildingType t = type != null ? type : BuildingType.DIVERS;
            ps.setString(11, t.name());
            ps.setString(12, t == BuildingType.ATELIER ? reservedFor : null);
            setNullableInt(ps, 13, yMin);
            setNullableInt(ps, 14, yMax);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) ps.setInt(index, value);
        else ps.setNull(index, java.sql.Types.INTEGER);
    }

    private Building buildFromResultSet(ResultSet rs) throws SQLException {
        double npcX = rs.getDouble("npc_x");
        double npcY = rs.getDouble("npc_y");
        double npcZ = rs.getDouble("npc_z");
        float npcYaw = rs.getFloat("npc_yaw");
        boolean hasPoint = !rs.wasNull();
        BuildingType type = BuildingType.parse(rs.getString("type"));
        int yMin = rs.getInt("y_min");
        boolean hasMin = !rs.wasNull();
        int yMax = rs.getInt("y_max");
        boolean hasMax = !rs.wasNull();
        return new Building(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("world"),
                rs.getInt("x1"), rs.getInt("z1"),
                rs.getInt("x2"), rs.getInt("z2"),
                rs.getString("npc_tag"),
                hasPoint ? npcX : null,
                hasPoint ? npcY : null,
                hasPoint ? npcZ : null,
                hasPoint ? npcYaw : null,
                type != null ? type : BuildingType.DIVERS,
                rs.getString("reserved_for"),
                hasMin ? yMin : null,
                hasMax ? yMax : null,
                rs.getInt("level")
        );
    }

    /** Tous les bâtiments, dans l'ordre d'affichage (type, puis nom). */
    public List<Building> getAllBuildings() {
        List<Building> list = new ArrayList<>();
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT * FROM buildings");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(buildFromResultSet(rs));
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        list.sort(DISPLAY_ORDER);
        return list;
    }

    /** Bâtiment par son identifiant, ou null. */
    public Building getById(int id) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT * FROM buildings WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? buildFromResultSet(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Niveau de construction (phase 2 : calculé par BuildingInspector). */
    public void setLevel(int buildingId, int level) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("UPDATE buildings SET level = ? WHERE id = ?")) {
            ps.setInt(1, level);
            ps.setInt(2, buildingId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Bâtiment par son nom (casse ignorée), ou null. */
    public Building getByName(String name) {
        return getAllBuildings().stream()
                .filter(b -> b.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
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
        Building removed = getByName(name);
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "DELETE FROM buildings WHERE LOWER(name) = LOWER(?)");
            ps.setString(1, name);
            int affected = ps.executeUpdate();
            ps.close();
            if (affected > 0 && removed != null) evict(List.of(removed));
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void removeAll() {
        List<Building> removed = getAllBuildings();
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "DELETE FROM buildings");
            ps.executeUpdate();
            ps.close();
            evict(removed);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Assigne un NPC à un bâtiment par son id. L'atelier lui est désormais réservé.
     */
    public boolean assignNPC(int buildingId, String npcTag) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            UPDATE buildings SET npc_tag = ?, reserved_for = ? WHERE id = ?
        """);
            ps.setString(1, npcTag);
            ps.setString(2, npcTag);
            ps.setInt(3, buildingId);
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
        List<Building> removed = getAllBuildings().stream().filter(b -> b.id() == id).toList();
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "DELETE FROM buildings WHERE id = ?");
            ps.setInt(1, id);
            if (ps.executeUpdate() > 0) evict(removed);
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

    /**
     * Libère les bâtiments assignés à un PNJ qui n'existe plus (PNJ de test supprimé…).
     * @return nombre de bâtiments libérés
     */
    public int releaseUnknownNpcTags(java.util.Set<String> knownTags) {
        if (knownTags.isEmpty()) return 0;
        String placeholders = String.join(",", java.util.Collections.nCopies(knownTags.size(), "?"));
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE buildings SET npc_tag = NULL WHERE npc_tag IS NOT NULL AND npc_tag NOT IN ("
                            + placeholders + ")");
            int i = 1;
            for (String tag : knownTags) ps.setString(i++, tag);
            int affected = ps.executeUpdate();
            ps.close();
            // Les réservations d'ateliers suivent la même règle
            PreparedStatement rs = db.getConnection().prepareStatement(
                    "UPDATE buildings SET reserved_for = NULL WHERE reserved_for IS NOT NULL AND reserved_for NOT IN ("
                            + placeholders + ")");
            i = 1;
            for (String tag : knownTags) rs.setString(i++, tag);
            rs.executeUpdate();
            rs.close();
            return affected;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
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

    /* =========================
       TYPES ET HAUTEUR (phase 1 du plan « Valmont vivante »)
       ========================= */

    /** Change le type (et la réservation d'un atelier ; ignorée pour les autres types). */
    public boolean setType(int buildingId, BuildingType type, String reservedFor) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE buildings SET type = ?, reserved_for = ? WHERE id = ?")) {
            ps.setString(1, type.name());
            ps.setString(2, type == BuildingType.ATELIER ? reservedFor : null);
            ps.setInt(3, buildingId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean setHeight(int buildingId, int yMin, int yMax) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE buildings SET y_min = ?, y_max = ? WHERE id = ?")) {
            ps.setInt(1, Math.min(yMin, yMax));
            ps.setInt(2, Math.max(yMin, yMax));
            ps.setInt(3, buildingId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** La mairie (hors du bâtiment excludeId), ou null : il n'y en a qu'une. */
    public Building findMairie(int excludeId) {
        return getAllBuildings().stream()
                .filter(b -> b.type() == BuildingType.MAIRIE && b.id() != excludeId)
                .findFirst().orElse(null);
    }

    /**
     * Bâtiment déjà réservé à cet artisan ou habité par lui (hors excludeId), ou null :
     * un personnage n'a qu'un seul atelier.
     */
    public Building findReservedFor(String npcTag, int excludeId) {
        return getAllBuildings().stream()
                .filter(b -> b.id() != excludeId)
                .filter(b -> npcTag.equals(b.reservedFor()) || npcTag.equals(b.npcTag()))
                .findFirst().orElse(null);
    }

    /** Ateliers où cet artisan peut s'installer : ceux qui lui sont réservés d'abord, puis les libres. */
    public List<Building> freeAteliersFor(CityNPC npc) {
        return getAllBuildings().stream()
                .filter(b -> b.acceptsArtisan(npc))
                .sorted(Comparator.comparing((Building b) -> b.reservedFor() == null)
                        .thenComparing(Building::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /**
     * Estime la hauteur des bâtiments qui n'en ont pas (créés avant la phase 1).
     * Charge les chunks concernés : à appeler une fois, sur le fil principal.
     * @return nombre de bâtiments complétés
     */
    public int estimateMissingHeights(org.bukkit.Server server) {
        int done = 0;
        for (Building b : getAllBuildings()) {
            if (b.hasHeight()) continue;
            org.bukkit.World world = server.getWorld(b.world());
            if (world == null) continue;
            int[] h = BuildingHeights.estimate(world, b.x1(), b.z1(), b.x2(), b.z2(), null, null);
            if (setHeight(b.id(), h[0], h[1])) done++;
        }
        return done;
    }
}
