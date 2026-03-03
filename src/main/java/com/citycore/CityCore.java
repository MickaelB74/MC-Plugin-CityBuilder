package com.citycore;

import com.citycore.building.*;
import com.citycore.chunk.ClaimProtectionListener;
import com.citycore.city.CityManager;
import com.citycore.command.CityCommand;
import com.citycore.command.CityTabCompleter;
import com.citycore.npc.*;
import com.citycore.npc.mayor.MayorGUI;
import com.citycore.npc.mayor.MayorListener;
import com.citycore.npc.mayor.MayorQuestGUI;
import com.citycore.npc.villager.*;
import com.citycore.player.PlayerDataManager;
import com.citycore.quest.*;
import com.citycore.quest.city.CityQuestManager;
import com.citycore.quest.city.FindNpcQuestListener;
import com.citycore.quest.city.FindNpcQuestManager;
import com.citycore.util.ChatInputManager;
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

    private DatabaseManager        databaseManager;
    private CityManager            cityManager;
    private NPCManager             npcManager;
    private Economy                economy;
    private NPCNotificationManager notificationManager;
    private CityCoreHUD            cityHUD;
    private PlayerDataManager      playerDataManager;
    private CityQuestManager       cityQuestManager;
    FindNpcQuestManager            findNpcQuestManager;

    @Override
    public void onEnable() {

        // ── Base de données ──────────────────────────────────────
        databaseManager = new DatabaseManager(this);
        databaseManager.openDatabase();

        // ── Vault ────────────────────────────────────────────────
        economy = setupEconomy();
        if (economy == null) {
            getLogger().severe("❌ Vault/Economy introuvable — désactivation.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ── ChatInput ────────────────────────────────────────────
        ChatInputManager.init(this);

        // ── Managers de base ─────────────────────────────────────
        cityManager         = new CityManager(databaseManager, this);
        npcManager          = new NPCManager(this);
        notificationManager = new NPCNotificationManager(databaseManager);
        playerDataManager   = new PlayerDataManager(databaseManager);

        // ── Managers partagés ────────────────────────────────────
        NPCDataManager      npcDataManager = new NPCDataManager(databaseManager);
        IntroductionManager introManager   = new IntroductionManager(databaseManager);
        QuestManager        questManager   = new QuestManager(databaseManager);

        // ── Bâtiments ────────────────────────────────────────────
        BuildingManager    buildingManager    = new BuildingManager(databaseManager);
        BuildingSession    buildingSession    = new BuildingSession();
        BuildingGUI        buildingGUI        = new BuildingGUI(buildingManager);
        BuildingBorderTask buildingBorderTask = new BuildingBorderTask(this, buildingManager);

        getServer().getPluginManager().registerEvents(
                new BuildingListener(buildingSession, buildingManager, cityManager), this);
        getServer().getPluginManager().registerEvents(
                new BuildingGUIListener(buildingManager, this), this);
        getServer().getPluginManager().registerEvents(
                new BuildingEnterListener(buildingManager, cityManager), this);

        // ── CityQuestManager (après buildingManager + npcDataManager) ──
        cityQuestManager = new CityQuestManager(
                databaseManager, cityManager, buildingManager, npcDataManager, this);
        cityQuestManager.startSyncTask();

        findNpcQuestManager = new FindNpcQuestManager(
                this, npcManager, npcDataManager, cityManager, cityQuestManager);

        getServer().getPluginManager().registerEvents(
                new FindNpcQuestListener(findNpcQuestManager), this);

        // ── HUD ──────────────────────────────────────────────────
        cityHUD = new CityCoreHUD(this, cityManager, economy, playerDataManager);
        cityHUD.start();

        // ── Maire ────────────────────────────────────────────────
        MayorQuestGUI  mayorQuestGUI = new MayorQuestGUI(cityQuestManager, findNpcQuestManager);
        MayorGUI       mayorGUI      = new MayorGUI(cityManager, npcManager,
                buildingManager, economy, mayorQuestGUI, cityQuestManager, this);
        NPCGuiRegistry guiRegistry   = new NPCGuiRegistry();
        guiRegistry.register(CityNPC.MAYOR, mayorGUI);

        getServer().getPluginManager().registerEvents(
                new MayorListener(npcManager, guiRegistry, introManager, cityManager, this), this);

        // ── NPCs génériques ───────────────────────────────────────
        Map<CityNPC, VillagerConfig> villagerConfigMap = new HashMap<>();
        List<QuestGUI>               questGUIs         = new ArrayList<>();

        for (CityNPC npcType : CityNPC.values()) {
            if (npcType == CityNPC.MAYOR) continue;

            VillagerConfig config     = new VillagerConfig(this, npcType.skinId);
            VillagerGUI    gui        = new VillagerGUI(npcType, config,
                    npcDataManager, npcManager, playerDataManager, this);
            QuestConfig    questConfig = new QuestConfig(this, npcType.skinId);
            QuestGUI       questGUI   = new QuestGUI(npcType, questConfig,
                    questManager, npcDataManager);

            questGUIs.add(questGUI);
            villagerConfigMap.put(npcType, config);

            getServer().getPluginManager().registerEvents(
                    new VillagerListener(npcType, gui, npcManager,
                            npcDataManager, economy, cityManager, introManager,
                            questGUI, this, notificationManager,
                            buildingManager, playerDataManager), this);
        }

        // ── HUD Quêtes NPC ────────────────────────────────────────
        QuestHUD questHUD = new QuestHUD(this, questManager, npcDataManager, questGUIs);
        questHUD.startUpdating();

        getServer().getPluginManager().registerEvents(
                new QuestListener(questGUIs, questManager, npcDataManager,
                        economy, this, villagerConfigMap, questHUD,
                        npcManager, notificationManager), this);

        // ── Chunks ────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(cityManager), this);

        // ── Commandes ────────────────────────────────────────────
        var cityCmd = getCommand("city");
        cityCmd.setExecutor(new CityCommand(cityManager, npcManager, this,
                npcDataManager, questHUD, buildingManager, buildingSession,
                buildingGUI, buildingBorderTask, notificationManager, playerDataManager));
        cityCmd.setTabCompleter(new CityTabCompleter(cityManager, buildingManager, npcManager));

        // ── Listeners globaux ─────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new ChunkListener(cityManager, npcManager, cityHUD, this, findNpcQuestManager), this);

        // ── Restauration NPCs ─────────────────────────────────────
        Bukkit.getScheduler().runTaskLater(this, () -> npcManager.restoreNPCs(), 20L);

        new NPCCityArrivalTask(npcManager, npcDataManager, cityManager,
                this, buildingManager, notificationManager, findNpcQuestManager).start();

        // ── WorldEdit ─────────────────────────────────────────────
        getServer().getMessenger().registerOutgoingPluginChannel(this, "worldedit:cui");

        getLogger().info("CityCore enabled ✅");
    }

    @Override
    public void onDisable() {
        if (cityHUD != null) cityHUD.stop();
        databaseManager.closeDatabase();
        getLogger().info("CityCore disabled");
    }

    private Economy setupEconomy() {
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public Economy         getEconomy()         { return economy; }
}