package com.citycore;

import org.bukkit.plugin.java.JavaPlugin;

public class CityCore extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("CityCore enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("CityCore disabled");
    }
}