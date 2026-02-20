package com.citycore;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCGuiRegistry;
import com.citycore.npc.stonemason.StonemasonConfig;
import com.citycore.npc.stonemason.StonemasonGUI;
import com.citycore.npc.stonemason.StonemasonListener;
import com.citycore.util.ChunkListener;
import com.citycore.city.CityManager;
import com.citycore.command.CityCommand;
import com.citycore.command.CityTabCompleter;
import com.citycore.npc.GUI.MayorGUI;
import com.citycore.npc.MayorListener;
import com.citycore.npc.NPCManager;
import com.citycore.util.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class CityCore extends JavaPlugin {

    private DatabaseManager databaseManager;
    private CityManager cityManager;
    private NPCManager npcManager;
    private Economy economy;

    @Override
    public void onEnable() {
        databaseManager = new DatabaseManager(this);
        databaseManager.openDatabase();

        cityManager = new CityManager(databaseManager);
        npcManager = new NPCManager(this);

        // Vault Economy
        economy = setupEconomy();
        if (economy == null) {
            getLogger().severe("❌ Vault/Economy introuvable — désactivation du plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Registre des GUIs NPC
        NPCGuiRegistry guiRegistry = new NPCGuiRegistry();
        guiRegistry.register(CityNPC.MAYOR, new MayorGUI(cityManager, npcManager));

        // Config et GUI du tailleur
        StonemasonConfig stonemasonConfig = new StonemasonConfig(this);
        StonemasonGUI stonemasonGUI = new StonemasonGUI(stonemasonConfig, npcManager);

        getServer().getPluginManager().registerEvents(
                new StonemasonListener(npcManager, stonemasonGUI, stonemasonConfig, economy), this);

        // Futurs NPCs :
        // guiRegistry.register(CityNPC.BLACKSMITH, new BlacksmithGUI(cityManager));
        // guiRegistry.register(CityNPC.MERCHANT,   new MerchantGUI(cityManager));


        var cityCmd = getCommand("city");
        cityCmd.setExecutor(new CityCommand(cityManager, npcManager, this));
        cityCmd.setTabCompleter(new CityTabCompleter(cityManager));

        getServer().getPluginManager().registerEvents(
                new ChunkListener(cityManager, npcManager), this);
        getServer().getPluginManager().registerEvents(
                new MayorListener(npcManager, guiRegistry), this);

        // ✅ Restauration des NPCs après 1 tick (Citizens finit son chargement d'abord)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            npcManager.restoreNPCs();
        }, 20L);

        getLogger().info("CityCore enabled");
    }

    @Override
    public void onDisable() {
        databaseManager.closeDatabase();
        getLogger().info("CityCore disabled");
    }

    private Economy setupEconomy() {
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}