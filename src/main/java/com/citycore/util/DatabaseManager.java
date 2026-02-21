package com.citycore.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
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
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

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
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL DEFAULT 'Ma Ville',
                level INTEGER NOT NULL,
                coins INTEGER NOT NULL,
                max_chunks INTEGER NOT NULL DEFAULT 1
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY,
                coins INTEGER NOT NULL DEFAULT 0
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

        stmt.close();
    }

    public Connection getConnection() {
        return connection;
    }

    public void closeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}