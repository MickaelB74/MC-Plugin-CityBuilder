package com.citycore;

import com.citycore.building.*;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CityCore extends JavaPlugin {

    private DatabaseManager databaseManager;
    private CityManager     cityManager;
    private NPCManager      npcManager;
    private Economy         economy;
    private NPCNotificationManager notificationManager;

    @Override
    public void onEnable() {
        // ── Base de données ──────────────────────────────────────
        databaseManager = new DatabaseManager(this);
        databaseManager.openDatabase();

        cityManager = new CityManager(databaseManager);
        npcManager  = new NPCManager(this);
        notificationManager = new NPCNotificationManager(databaseManager);

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

        // ── build ────────────────────────────────────
        BuildingManager buildingManager = new BuildingManager(databaseManager);
        BuildingSession buildingSession = new BuildingSession();
        BuildingGUI         buildingGUI     = new BuildingGUI(buildingManager);
        BuildingBorderTask buildingBorderTask = new BuildingBorderTask(this, buildingManager);

        getServer().getPluginManager().registerEvents(
                new BuildingListener(buildingSession, buildingManager, cityManager), this);
        getServer().getPluginManager().registerEvents(
                new BuildingGUIListener(buildingManager), this);
        getServer().getPluginManager().registerEvents(
                new BuildingEnterListener(buildingManager, cityManager), this);

        // ── Maire ────────────────────────────────────────────────
        MayorGUI       mayorGUI    = new MayorGUI(cityManager, npcManager);
        NPCGuiRegistry guiRegistry = new NPCGuiRegistry();
        guiRegistry.register(CityNPC.MAYOR, mayorGUI);

        getServer().getPluginManager().registerEvents(
                new MayorListener(npcManager, guiRegistry, introManager, this), this);

        // ── NPCs génériques ───────────────────────────────────────
        Map<CityNPC, VillagerConfig> villagerConfigMap = new HashMap<>();
        List<QuestGUI> questGUIs = new ArrayList<>();

        for (CityNPC npcType : CityNPC.values()) {
            if (npcType == CityNPC.MAYOR) continue; // MAYOR a son propre listener

            VillagerConfig config = new VillagerConfig(this, npcType.skinId);
            VillagerGUI    gui    = new VillagerGUI(npcType, config,
                    npcDataManager, npcManager);

            QuestConfig questConfig = new QuestConfig(this, npcType.skinId);
            QuestGUI    questGUI    = new QuestGUI(npcType, questConfig,
                    questManager, npcDataManager);

            questGUIs.add(questGUI);
            villagerConfigMap.put(npcType, config);

            getServer().getPluginManager().registerEvents(
                    new VillagerListener(npcType, gui, npcManager,
                            npcDataManager, economy, cityManager, introManager,
                            questGUI, this, notificationManager, buildingManager), this);
        }

        // ── HUD Quest ─────────────────────────────────────────────
        QuestHUD questHUD = new QuestHUD(this, questManager,
                npcDataManager, questGUIs);
        questHUD.startUpdating();

        getServer().getPluginManager().registerEvents(
                new QuestListener(questGUIs, questManager, npcDataManager,
                        economy, this, villagerConfigMap, questHUD,
                        npcManager, notificationManager), this);

        // ── Commandes ────────────────────────────────────────────
        var cityCmd = getCommand("city");
        cityCmd.setExecutor(new CityCommand(cityManager, npcManager, this,
                npcDataManager, questHUD, buildingManager, buildingSession, buildingGUI, buildingBorderTask, notificationManager));
        cityCmd.setTabCompleter(new CityTabCompleter(cityManager, buildingManager, npcManager));

        // ── Listeners globaux ────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new ChunkListener(cityManager, npcManager), this);

        // ── Restauration NPCs ────────────────────────────────────
        Bukkit.getScheduler().runTaskLater(this, () ->
                npcManager.restoreNPCs(), 20L);

        new NPCCityArrivalTask(npcManager, npcDataManager, cityManager, this, buildingManager, notificationManager).start();

        // ── WorldEdit ────────────────────────────────────
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, "worldedit:cui");

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