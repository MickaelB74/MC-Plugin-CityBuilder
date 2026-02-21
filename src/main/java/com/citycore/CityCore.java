package com.citycore;

import com.citycore.city.CityManager;
import com.citycore.command.CityCommand;
import com.citycore.command.CityTabCompleter;
import com.citycore.npc.*;
import com.citycore.npc.mayor.MayorGUI;
import com.citycore.npc.mayor.MayorListener;
import com.citycore.npc.villager.*;
import com.citycore.quest.*;
import com.citycore.util.ChunkListener;
import com.citycore.util.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class CityCore extends JavaPlugin {

    private DatabaseManager databaseManager;
    private CityManager     cityManager;
    private NPCManager      npcManager;
    private Economy         economy;

    @Override
    public void onEnable() {
        // ── Base de données ──────────────────────────────────────
        databaseManager = new DatabaseManager(this);
        databaseManager.openDatabase();

        cityManager = new CityManager(databaseManager);
        npcManager  = new NPCManager(this);

        // ── Vault ────────────────────────────────────────────────
        economy = setupEconomy();
        if (economy == null) {
            getLogger().severe("❌ Vault/Economy introuvable — désactivation.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ── Managers partagés ────────────────────────────────────
        NPCDataManager      npcDataManager = new NPCDataManager(databaseManager);
        IntroductionManager introManager   = new IntroductionManager(databaseManager);
        QuestManager        questManager   = new QuestManager(databaseManager);

        // ── Maire ────────────────────────────────────────────────
        MayorGUI       mayorGUI    = new MayorGUI(cityManager, npcManager);
        NPCGuiRegistry guiRegistry = new NPCGuiRegistry();
        guiRegistry.register(CityNPC.MAYOR, mayorGUI);

        getServer().getPluginManager().registerEvents(
                new MayorListener(npcManager, guiRegistry, introManager, this), this);

        // ── Tailleur de pierre ───────────────────────────────────
        VillagerConfig stonemasonConfig = new VillagerConfig(this, "stonemason");
        VillagerGUI    stonemasonGUI    = new VillagerGUI(CityNPC.STONEMASON,
                stonemasonConfig, npcDataManager, npcManager);

        QuestConfig stonemasonQuestConfig = new QuestConfig(this, "stonemason");
        QuestGUI    stonemasonQuestGUI    = new QuestGUI(CityNPC.STONEMASON,
                stonemasonQuestConfig, questManager, npcDataManager);

        getServer().getPluginManager().registerEvents(
                new VillagerListener(CityNPC.STONEMASON, stonemasonGUI, npcManager,
                        npcDataManager, economy, cityManager, introManager,
                        stonemasonQuestGUI, this), this);

        // ── Jack Sparrow ─────────────────────────────────────────
        VillagerConfig jackConfig = new VillagerConfig(this, "jacksparrow");
        VillagerGUI    jackGUI    = new VillagerGUI(CityNPC.JACKSPARROW,
                jackConfig, npcDataManager, npcManager);

        QuestConfig jackQuestConfig = new QuestConfig(this, "jacksparrow");
        QuestGUI    jackQuestGUI    = new QuestGUI(CityNPC.JACKSPARROW,
                jackQuestConfig, questManager, npcDataManager);

        getServer().getPluginManager().registerEvents(
                new VillagerListener(CityNPC.JACKSPARROW, jackGUI, npcManager,
                        npcDataManager, economy, cityManager, introManager,
                        jackQuestGUI, this), this);

        // ── QuestListener global ─────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new QuestListener(
                        List.of(stonemasonQuestGUI, jackQuestGUI),
                        questManager, npcDataManager, economy), this);

        // ── Commandes ────────────────────────────────────────────
        var cityCmd = getCommand("city");
        cityCmd.setExecutor(new CityCommand(cityManager, npcManager,
                this, npcDataManager));
        cityCmd.setTabCompleter(new CityTabCompleter(cityManager));

        // ── Listeners globaux ────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new ChunkListener(cityManager, npcManager), this);

        // ── Restauration NPCs ────────────────────────────────────
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

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public Economy getEconomy()                 { return economy; }
}