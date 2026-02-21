package com.citycore;

import com.citycore.npc.CityNPC;
import com.citycore.npc.IntroductionManager;
import com.citycore.npc.NPCGuiRegistry;
import com.citycore.npc.jacksparrow.JackSparrowConfig;
import com.citycore.npc.jacksparrow.JackSparrowGUI;
import com.citycore.npc.jacksparrow.JackSparrowListener;
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

public class CityCore extends JavaPlugin {

    private DatabaseManager databaseManager;
    private CityManager cityManager;
    private NPCManager npcManager;
    private Economy economy;

    @Override
    public void onEnable() {
        // Base de données
        databaseManager = new DatabaseManager(this);
        databaseManager.openDatabase();

        cityManager = new CityManager(databaseManager);
        npcManager  = new NPCManager(this);

        // Vault Economy
        economy = setupEconomy();
        if (economy == null) {
            getLogger().severe("❌ Vault/Economy introuvable — désactivation du plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Introduction manager (premier clic NPC stocké en BDD)
        IntroductionManager introManager = new IntroductionManager(databaseManager);

        // ── Maire ────────────────────────────────────────────────
        MayorGUI mayorGUI = new MayorGUI(cityManager, npcManager);
        NPCGuiRegistry guiRegistry = new NPCGuiRegistry();
        guiRegistry.register(CityNPC.MAYOR, mayorGUI);

        getServer().getPluginManager().registerEvents(
                new MayorListener(npcManager, guiRegistry, introManager, this), this);

        // ── Tailleur de pierre ───────────────────────────────────
        StonemasonConfig stonemasonConfig = new StonemasonConfig(this);
        StonemasonGUI stonemasonGUI = new StonemasonGUI(stonemasonConfig, npcManager);

        getServer().getPluginManager().registerEvents(
                new StonemasonListener(npcManager, stonemasonGUI, stonemasonConfig,
                        economy, introManager, this), this);

        // ── Jack Sparrow ─────────────────────────────────────────
        JackSparrowConfig jackConfig = new JackSparrowConfig(this);
        JackSparrowGUI JackSparrowGUI = new JackSparrowGUI(jackConfig, npcManager);

        getServer().getPluginManager().registerEvents(
                new JackSparrowListener(npcManager, JackSparrowGUI, jackConfig,
                        economy, introManager, this), this);

        // Futurs NPCs :
        // guiRegistry.register(CityNPC.BLACKSMITH, new BlacksmithGUI(cityManager));
        // guiRegistry.register(CityNPC.MERCHANT,   new MerchantGUI(cityManager));

        // ── Commandes ────────────────────────────────────────────
        var cityCmd = getCommand("city");
        cityCmd.setExecutor(new CityCommand(cityManager, npcManager, this));
        cityCmd.setTabCompleter(new CityTabCompleter(cityManager));

        // ── Listeners globaux ────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new ChunkListener(cityManager, npcManager), this);

        // ── Restauration des NPCs Citizens après chargement ──────
        Bukkit.getScheduler().runTaskLater(this, () ->
                npcManager.restoreNPCs(), 20L);

        getLogger().info("CityCore enabled ✅");
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

    public Economy getEconomy() {
        return economy;
    }
}