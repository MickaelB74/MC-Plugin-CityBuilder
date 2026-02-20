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
            // 🔹 Création automatique du dossier plugins/CityCore
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            // 🔹 Connexion SQLite
            File dbFile = new File(dataFolder, "database.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            // 🔹 Exemple de table
            Statement stmt = connection.createStatement();
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    coins INTEGER DEFAULT 0
                )
            """);
            stmt.close();

            plugin.getLogger().info("SQLite initialisée avec succès !");
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur SQLite !");
            e.printStackTrace();
        }
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