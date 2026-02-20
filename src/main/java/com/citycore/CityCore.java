package com.citycore;

import com.citycore.util.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public class CityCore extends JavaPlugin {

    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        databaseManager = new DatabaseManager(this);
        databaseManager.openDatabase();

        getLogger().info("CityCore enabled");
    }

    @Override
    public void onDisable() {
        databaseManager.closeDatabase();

        getLogger().info("CityCore disabled");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}