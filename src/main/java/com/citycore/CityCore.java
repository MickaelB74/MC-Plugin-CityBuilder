package com.citycore;

import com.citycore.util.ChunkListener;
import com.citycore.city.CityManager;
import com.citycore.command.CityCommand;
import com.citycore.command.CityTabCompleter;
import com.citycore.npc.GUI.MayorGUI;
import com.citycore.npc.MayorListener;
import com.citycore.npc.NPCManager;
import com.citycore.util.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class CityCore extends JavaPlugin {

    private DatabaseManager databaseManager;
    private CityManager cityManager;
    private NPCManager npcManager;

    @Override
    public void onEnable() {
        File skinFile = new File(getDataFolder(), "skins/alderic.png");
        getLogger().info("Skin existe : " + skinFile.exists());
        getLogger().info("Chemin : " + skinFile.getAbsolutePath());

        databaseManager = new DatabaseManager(this);
        databaseManager.openDatabase();

        cityManager = new CityManager(databaseManager);
        npcManager = new NPCManager(this);
        MayorGUI mayorGUI = new MayorGUI(cityManager, npcManager);

        var cityCmd = getCommand("city");
        cityCmd.setExecutor(new CityCommand(cityManager, npcManager, this));
        cityCmd.setTabCompleter(new CityTabCompleter(cityManager));

        getServer().getPluginManager().registerEvents(
                new ChunkListener(cityManager, npcManager), this);
        getServer().getPluginManager().registerEvents(
                new MayorListener(npcManager, mayorGUI, cityManager), this);

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