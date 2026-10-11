package com.citycore.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void openDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            File dbFile = new File(dataFolder, "database.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            initializeTables();

            plugin.getLogger().info("SQLite initialisée avec succès !");
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur SQLite !");
            e.printStackTrace();
        }
    }

    private void initializeTables() throws SQLException {
        Statement stmt = connection.createStatement();

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS city (
                id          INTEGER PRIMARY KEY,
                name        TEXT    NOT NULL DEFAULT 'Ma Ville',
                level       INTEGER NOT NULL,
                coins       INTEGER NOT NULL,
                max_chunks  INTEGER NOT NULL DEFAULT 1,
                residents   INTEGER NOT NULL DEFAULT 0
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS players (
                uuid    TEXT PRIMARY KEY,
                xp      INTEGER NOT NULL DEFAULT 0,
                level   INTEGER NOT NULL DEFAULT 1,
                job     TEXT
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS claimed_chunks (
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                z INTEGER NOT NULL,
                PRIMARY KEY (world, x, z)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS npcs (
                npc_id TEXT PRIMARY KEY,
                unlocked INTEGER NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS quests (
                quest_id TEXT PRIMARY KEY,
                completed INTEGER NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS npc_introductions (
                player_uuid TEXT NOT NULL,
                npc_tag TEXT NOT NULL,
                PRIMARY KEY (player_uuid, npc_tag)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS npc_data (
                npc_tag  TEXT PRIMARY KEY,
                xp       INTEGER NOT NULL DEFAULT 0,
                level    INTEGER NOT NULL DEFAULT 1,
                state    TEXT NOT NULL DEFAULT 'WANDERER'
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS npc_inventory (
                npc_tag TEXT NOT NULL,
                material TEXT NOT NULL,
                amount INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (npc_tag, material)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS quest_progress (
                player_uuid  TEXT NOT NULL,
                npc_tag      TEXT NOT NULL,
                is_special   INTEGER NOT NULL DEFAULT 0,
                quest_data   TEXT NOT NULL,
                progress     TEXT NOT NULL DEFAULT '',
                completed    INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, npc_tag, is_special)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS buildings (
                id       INTEGER PRIMARY KEY AUTOINCREMENT,
                name     TEXT NOT NULL,
                world    TEXT NOT NULL,
                x1       INTEGER NOT NULL,
                z1       INTEGER NOT NULL,
                x2       INTEGER NOT NULL,
                z2       INTEGER NOT NULL,
                npc_tag  TEXT,
                npc_x    REAL,
                npc_y    REAL,
                npc_z    REAL,
                npc_yaw  REAL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS npc_notifications (
                player_uuid TEXT NOT NULL,
                npc_tag     TEXT NOT NULL,
                has_notif   INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, npc_tag)
            )
        """);

        // Dans initializeTables()
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS city_quest_progress (
                quest_id     TEXT    PRIMARY KEY,
                current_val  INTEGER NOT NULL DEFAULT 0,
                completed    INTEGER NOT NULL DEFAULT 0
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS city_level (
                id    INTEGER PRIMARY KEY DEFAULT 1,
                level INTEGER NOT NULL DEFAULT 1
            )
        """);

        // Init du niveau si vide
        stmt.execute("""
            INSERT OR IGNORE INTO city_level (id, level) VALUES (1, 1)
        """);

        // Découpage des chapitres auquel se rapporte le niveau (7 = ancienne trame, 10 = actuelle)
        try {
            stmt.execute("ALTER TABLE city_level ADD COLUMN scheme INTEGER NOT NULL DEFAULT 7");
        } catch (SQLException ignored) {
            // colonne déjà présente
        }

        // Bâtiments : type, atelier réservé, hauteur et niveau (phase 1 du plan « Valmont vivante »)
        boolean typesAdded = addColumn(stmt, "buildings", "type TEXT NOT NULL DEFAULT 'DIVERS'");
        addColumn(stmt, "buildings", "reserved_for TEXT");
        addColumn(stmt, "buildings", "y_min INTEGER");
        addColumn(stmt, "buildings", "y_max INTEGER");
        addColumn(stmt, "buildings", "level INTEGER NOT NULL DEFAULT 0");
        if (typesAdded) {
            // Première fois : un bâtiment habité par un artisan devient son atelier, les autres « Divers »
            int converted = stmt.executeUpdate(
                    "UPDATE buildings SET type = 'ATELIER', reserved_for = npc_tag WHERE npc_tag IS NOT NULL");
            plugin.getLogger().info("Bâtiments : types ajoutés (" + converted
                    + " atelier(s) d'artisan, les autres en « Divers »).");
        }
        // Point de définition (sol) : la hauteur devient « profondeur dessous, hauteur au-dessus ».
        // Première fois : l'ancien bas de zone (sol estimé ou coins cliqués) sert de point de définition.
        if (addColumn(stmt, "buildings", "ref_y INTEGER")) {
            int up   = Math.max(1, plugin.getConfig().getInt("batiments.hauteur", 100));
            int down = Math.max(0, plugin.getConfig().getInt("batiments.profondeur", 40));
            int moved = stmt.executeUpdate("UPDATE buildings SET ref_y = y_min, y_min = y_min - " + down
                    + ", y_max = y_min + " + up + " WHERE y_min IS NOT NULL");
            plugin.getLogger().info("Bâtiments : hauteur recalculée pour " + moved + " bâtiment(s) ("
                    + down + " blocs sous le sol, " + up + " au-dessus).");
        }

        // Habitants de la ville (phase 3 du plan « Valmont vivante ») : un par lit d'habitation.
        // state : HABITANT, FORMATION, SOLDAT, MORT (un mort garde son lit le temps du deuil)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS inhabitants (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                name        TEXT    NOT NULL,
                skin        INTEGER NOT NULL DEFAULT -1,
                building_id INTEGER NOT NULL,
                world       TEXT    NOT NULL,
                bed_x       INTEGER NOT NULL,
                bed_y       INTEGER NOT NULL,
                bed_z       INTEGER NOT NULL,
                state       TEXT    NOT NULL DEFAULT 'HABITANT',
                arrived_at  INTEGER NOT NULL,
                died_at     INTEGER
            )
        """);
        // Garde (phase 5) : rôle (soldat, archer…), temps de formation restant (ms), uniforme
        addColumn(stmt, "inhabitants", "role TEXT");
        addColumn(stmt, "inhabitants", "training_left INTEGER NOT NULL DEFAULT 0");
        addColumn(stmt, "inhabitants", "uniform INTEGER NOT NULL DEFAULT -1");

        stmt.close();
    }

    /** Ajoute une colonne si elle manque ; renvoie true si elle vient d'être créée. */
    private static boolean addColumn(Statement stmt, String table, String definition) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + definition);
            return true;
        } catch (SQLException ignored) {
            return false; // colonne déjà présente
        }
    }

    public Connection getConnection() {
        return connection;
    }

    /* =========================
       REQUÊTES SIMPLES (ressources toujours fermées)
       sqlite-jdbc garde une référence à chaque Statement non fermé jusqu'à la fermeture
       de la connexion : sans fermeture, les requêtes périodiques (HUD, synchronisations)
       font grossir la mémoire pendant toute la durée de vie du serveur.
       ========================= */

    /** Colonne entière de la première ligne, ou {@code def} si aucune ligne / erreur. */
    public int queryInt(String sql, String column, int def) {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(column) : def;
        } catch (SQLException e) {
            e.printStackTrace();
            return def;
        }
    }

    /** Colonne texte de la première ligne, ou {@code def} si aucune ligne / erreur. */
    public String queryString(String sql, String column, String def) {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(column) : def;
        } catch (SQLException e) {
            e.printStackTrace();
            return def;
        }
    }

    /** Instruction sans paramètre (création de table, mise à jour). Propage l'erreur. */
    public void execute(String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    public void closeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
