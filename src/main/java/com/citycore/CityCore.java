package com.citycore;

import com.citycore.building.*;
import com.citycore.chunk.ClaimProtectionListener;
import com.citycore.city.CityManager;
import com.citycore.command.CityCommand;
import com.citycore.command.CityQuestSelectionGUI;
import com.citycore.command.CityQuestSelectionListener;
import com.citycore.command.CityTabCompleter;
import com.citycore.economy.EconomyValidator;
import com.citycore.economy.ItemTags;
import com.citycore.economy.MarketService;
import com.citycore.npc.*;
import com.citycore.npc.mayor.MayorGUI;
import com.citycore.npc.mayor.MayorListener;
import com.citycore.npc.mayor.MayorQuestGUI;
import com.citycore.npc.villager.*;
import com.citycore.player.PlayerDataManager;
import com.citycore.progress.CityAdvancements;
import com.citycore.progress.ProgressionService;
import com.citycore.quest.*;
import com.citycore.quest.city.CityQuestListener;
import com.citycore.quest.city.CityQuestManager;
import com.citycore.quest.city.FindNpcQuestListener;
import com.citycore.quest.city.FindNpcQuestManager;
import com.citycore.quest.personal.NPCPersonalQuestGUI;
import com.citycore.quest.personal.NPCPersonalQuestManager;
import com.citycore.quest.personal.PersonalQuestCatalog;
import com.citycore.summit.DragonListener;
import com.citycore.summit.PirateListener;
import com.citycore.summit.SummitListener;
import com.citycore.util.ChatInputManager;
import com.citycore.util.ChunkListener;
import com.citycore.util.ConfigUpdater;
import com.citycore.util.DatabaseManager;
import com.citycore.util.TypewriterUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
    private com.citycore.ui.UiPack uiPack;
    private com.citycore.debug.NpcPrototype prototype;
    private BuildingInspector buildingInspector;
    private com.citycore.inhabitant.InhabitantManager inhabitants;
    private com.citycore.siege.SiegeManager siege;
    private com.citycore.needs.NeedsManager needs;
    private com.citycore.jobs.JobsManager jobs;
    private com.citycore.city.CityChunkLoader chunkLoader;
    private com.citycore.city.CityMapExport   mapExport;

    @Override
    public void onEnable() {

        // ── Configuration : remplace un config.yml d'une version antérieure ──
        ConfigUpdater.ensureUpToDate(this);

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
        ItemTags.init(this);

        // ── Managers de base ─────────────────────────────────────
        cityManager         = new CityManager(databaseManager, this);
        npcManager          = new NPCManager(this);
        notificationManager = new NPCNotificationManager(databaseManager);
        playerDataManager   = new PlayerDataManager(databaseManager);
        TypewriterUtil.setCityNameSupplier(cityManager::getCityName);
        // Répliques plus larges que l'écran : coupées en morceaux successifs (dialogues.largeur)
        TypewriterUtil.setMaxWidth(getConfig().getInt("dialogues.largeur", 58));

        // ── Managers partagés ────────────────────────────────────
        NPCDataManager          npcDataManager       = new NPCDataManager(databaseManager);
        IntroductionManager     introManager         = new IntroductionManager(databaseManager);
        QuestManager            questManager         = new QuestManager(databaseManager, this);
        NPCPersonalQuestManager personalQuestManager = new NPCPersonalQuestManager(databaseManager);

        // ── Économie des PNJ (stocks, demande, revente) ──────────
        MarketService market = new MarketService(this, databaseManager, npcDataManager, playerDataManager);

        // ── Mods du serveur hybride (ex : Dragon Mounts Remastered pour Harold) ──
        com.citycore.integration.ModIntegrations modIntegrations = new com.citycore.integration.ModIntegrations(this);
        modIntegrations.detect();

        // ── Bâtiments ────────────────────────────────────────────
        BuildingManager    buildingManager    = new BuildingManager(databaseManager);
        BuildingSession    buildingSession    = new BuildingSession();
        BuildingGUI        buildingGUI        = new BuildingGUI(buildingManager);
        BuildingBorderTask buildingBorderTask = new BuildingBorderTask(this, buildingManager);
        // Un atelier ne se réserve qu'à un personnage trouvé : rencontré par un joueur, arrivé ou installé
        BuildingCreation   buildingCreation   = new BuildingCreation(buildingManager, buildingSession,
                npc -> npcDataManager.getState(npc) != NPCState.WANDERER || introManager.hasAnyoneMet(npc));
        // Inspection des constructions (phase 2) : exigences de config.yml (batiments), niveau, entrepôt
        buildingInspector = new BuildingInspector(this, buildingManager, new BuildingRequirements(this));
        buildingCreation.setInspector(buildingInspector);
        getServer().getPluginManager().registerEvents(buildingInspector, this);
        // Habitants (phase 3) : un par lit d'habitation conforme ; démarrés une fois le serveur prêt
        inhabitants = new com.citycore.inhabitant.InhabitantManager(this, databaseManager, buildingManager,
                buildingInspector, cityManager, npcDataManager, npcManager);
        getServer().getPluginManager().registerEvents(inhabitants, this);
        Bukkit.getScheduler().runTask(this, inhabitants::start);

        getServer().getPluginManager().registerEvents(
                new BuildingListener(buildingSession, buildingManager, cityManager, buildingCreation), this);
        // Bâtiment d'un artisan supprimé : il passe « sans logis » (gestion fermée, quêtes en pause,
        // rien n'est perdu) jusqu'à ce qu'on lui choisisse un nouvel atelier
        buildingManager.setOnNpcEvicted(tag -> {
            buildingInspector.onEvicted(tag); // plus de bonus d'entrepôt sans atelier
            buildingInspector.refreshBuildings();
            CityNPC evicted = CityNPC.fromTag(tag);
            if (evicted == null || npcDataManager.getState(evicted) != NPCState.ASSIGNED) return;
            npcDataManager.setState(evicted, NPCState.HOMELESS);
            npcManager.setWandering(evicted, true); // il se promène en ville en attendant
            net.citizensnpcs.api.npc.NPC citizen = npcManager.getNPC(evicted);
            if (citizen != null && citizen.isSpawned()) citizen.getNavigator().cancelNavigation();
            notificationManager.notifyAll(evicted, getServer(), npcManager);
            Bukkit.broadcastMessage("§6🏚 " + evicted.displayName + " §6n'a plus d'atelier. §7Ses quêtes sont en pause"
                    + " jusqu'à ce que vous lui choisissiez un nouveau logis (parlez-lui).");
            getLogger().info(ChatColor.stripColor(evicted.displayName) + " : bâtiment supprimé, passe sans logis.");
        });
        // Rattrapage : artisans installés dont le bâtiment a été supprimé avant ce correctif
        for (CityNPC type : CityNPC.values()) {
            if (type == CityNPC.MAYOR || npcDataManager.getState(type) != NPCState.ASSIGNED) continue;
            if (buildingManager.getAssignedBuilding(type.tag) != null) continue;
            npcDataManager.setState(type, NPCState.HOMELESS);
            npcManager.setWandering(type, true);
            getLogger().info(ChatColor.stripColor(type.displayName) + " : aucun bâtiment, passe sans logis.");
        }
        // Bâtiments créés avant les types : hauteur estimée une fois (charge leurs chunks)
        Bukkit.getScheduler().runTask(this, () -> {
            int estimated = buildingManager.estimateMissingHeights(getServer());
            if (estimated > 0) getLogger().info("Bâtiments : hauteur estimée pour " + estimated + " bâtiment(s).");
        });
        getServer().getPluginManager().registerEvents(
                new BuildingGUIListener(buildingManager, this), this);
        getServer().getPluginManager().registerEvents(
                new BuildingEnterListener(buildingManager, cityManager), this);

        // ── Données orphelines (PNJ de test supprimés, ex : Geralde) ──
        java.util.Set<String> knownTags = java.util.Arrays.stream(CityNPC.values())
                .map(n -> n.tag).collect(java.util.stream.Collectors.toSet());
        int freedBuildings = buildingManager.releaseUnknownNpcTags(knownTags);
        int clearedJobs    = playerDataManager.clearUnknownJobs(knownTags);
        if (freedBuildings + clearedJobs > 0) {
            getLogger().info("Nettoyage : " + freedBuildings + " bâtiment(s) libéré(s), "
                    + clearedJobs + " job(s) retiré(s) — PNJ disparus.");
        }

        // ── CityQuestManager (après buildingManager + npcDataManager) ──
        cityQuestManager = new CityQuestManager(
                databaseManager, cityManager, buildingManager, npcDataManager, market, this);
        cityQuestManager.migrateTierScheme(); // ancienne trame en 7 chapitres → 10
        cityQuestManager.startSyncTask();

        findNpcQuestManager = new FindNpcQuestManager(
                this, npcManager, npcDataManager, cityManager, cityQuestManager);
        // Piste de recrutement et annonce d'arrivée : ce que demandera l'atelier du personnage
        findNpcQuestManager.setAtelierRequest(buildingInspector::requestSentence);

        // ── Sièges et calendrier des événements (phase 4 du plan « Valmont vivante ») ──
        siege = new com.citycore.siege.SiegeManager(this, cityManager, buildingManager, inhabitants,
                npcDataManager, market, cityQuestManager::getCityLevel);
        getServer().getPluginManager().registerEvents(siege, this);
        Bukkit.getScheduler().runTask(this, siege::start); // assaillants d'un siège interrompu retirés
        com.citycore.siege.EventCalendar calendar = new com.citycore.siege.EventCalendar(this);
        calendar.register("siege", siege.asEvent());
        calendar.start();

        // ── Besoins et satisfaction des habitants (phase 6) ──────
        // Repas et soins à l'aube (entrepôts d'Hélène, Rosalie, Anselme), sécurité (garde, sièges),
        // confort (maisons) ; la satisfaction règle arrivées, impôt et départs
        needs = new com.citycore.needs.NeedsManager(this, databaseManager, inhabitants, npcDataManager, buildingInspector);
        needs.start();
        market.setConsumptionOverride(needs::abstractConsumption);
        siege.setResultListener(needs::recordSiege);

        // ── La ville vit où que soient les joueurs : ses chunks restent chargés (joueur connecté) ──
        chunkLoader = new com.citycore.city.CityChunkLoader(this, cityManager);
        getServer().getPluginManager().registerEvents(chunkLoader, this);
        chunkLoader.start();

        // ── Bordures de la ville pour les cartes des mods (JourneyMap, via le mod compagnon) ──
        mapExport = new com.citycore.city.CityMapExport(this, cityManager);
        mapExport.start();

        // ── Emplois : les habitants travaillent pour les artisans (annexes de travail) ──
        jobs = new com.citycore.jobs.JobsManager(this, buildingManager, buildingInspector, npcDataManager, market, inhabitants);
        jobs.start();

        getServer().getPluginManager().registerEvents(
                new FindNpcQuestListener(findNpcQuestManager), this);
        getServer().getPluginManager().registerEvents(
                new CityQuestListener(cityQuestManager), this);

        // ── HUD ──────────────────────────────────────────────────
        cityHUD = new CityCoreHUD(this, cityManager, economy, playerDataManager, cityQuestManager);
        cityHUD.setPopulation(inhabitants::populationLine); // « Habitants 12/16 » dans le panneau Tab
        cityHUD.setSatisfaction(needs::satisfaction);       // ligne « Satisfaction » (phase 6)
        cityHUD.start();

        // ── Interfaces illustrées « Valmont » (pack de ressources) ──
        com.citycore.ui.UiAtlas     uiAtlas     = new com.citycore.ui.UiAtlas();
        uiPack = new com.citycore.ui.UiPack(this, getFile(), uiAtlas);
        com.citycore.ui.UiPortraits uiPortraits = new com.citycore.ui.UiPortraits(this, npcManager);
        com.citycore.ui.UiKit       uiKit       = new com.citycore.ui.UiKit(this, uiAtlas, uiPack, uiPortraits, economy);
        if (uiPack != null && getConfig().getBoolean("ui-pack.enabled", true)) {
            try {
                uiPack.loadAssets();
                com.citycore.ui.UiLayout.declare(uiAtlas);
                uiPack.build();
                uiPack.startHosting();
            } catch (Exception e) {
                getLogger().warning("⚠ Pack d'interface indisponible (" + e.getMessage() + ") — menus classiques.");
            }
        }
        getServer().getPluginManager().registerEvents(uiPack, this);
        getServer().getPluginManager().registerEvents(new com.citycore.ui.UiListener(this), this);
        uiPortraits.loadAsync();
        cityHUD.setUi(uiKit); // bannière illustrée de la liste des joueurs (Tab)
        Bukkit.getScheduler().runTaskLater(this, uiPack::offerToOnlinePlayers, 40L);
        com.citycore.ui.screens.QuestScreens questScreens =
                new com.citycore.ui.screens.QuestScreens(uiKit, npcDataManager, personalQuestManager, market);

        // ── Maire ────────────────────────────────────────────────
        MayorQuestGUI  mayorQuestGUI = new MayorQuestGUI(cityQuestManager, findNpcQuestManager,
                cityManager, npcDataManager, this);
        MayorGUI       mayorGUI      = new MayorGUI(cityManager, npcManager,
                buildingManager, economy, mayorQuestGUI, cityQuestManager, this);
        NPCGuiRegistry guiRegistry   = new NPCGuiRegistry();
        guiRegistry.register(CityNPC.MAYOR, mayorGUI);
        // Choix du type des bâtiments : menu illustré (création, et clic droit dans la liste d'Alderic)
        com.citycore.ui.screens.BuildingScreens buildingScreens =
                new com.citycore.ui.screens.BuildingScreens(uiKit, buildingManager, buildingCreation, npcManager);
        buildingCreation.setChooser(buildingScreens::openNew);
        com.citycore.ui.screens.MayorScreens mayorScreens = new com.citycore.ui.screens.MayorScreens(
                uiKit, mayorGUI, cityManager, cityQuestManager, npcManager, buildingManager);
        mayorScreens.setBuildingScreens(buildingScreens);
        mayorScreens.setInspector(buildingInspector);
        mayorScreens.setOccupancy(inhabitants::occupancy); // habitants de chaque habitation
        mayorScreens.setPopulation(needs, inhabitants);     // clic sur le portrait d'Alderic : « Population »
        mayorGUI.setPopulationReport(needs::report);        // menu classique : bilan dans le chat
        mayorGUI.setThemed(mayorScreens);

        getServer().getPluginManager().registerEvents(
                new MayorListener(npcManager, guiRegistry, introManager, cityManager, this), this);

        // ── Progression : onglet de progrès Minecraft (touche L), seule vue d'ensemble ──
        ProgressionService progressionService = new ProgressionService(
                cityQuestManager, cityManager, npcDataManager, introManager);
        CityAdvancements cityAdvancements = new CityAdvancements(this, progressionService, npcManager);
        getServer().getPluginManager().registerEvents(cityAdvancements, this);
        Bukkit.getScheduler().runTask(this, () -> {
            cityAdvancements.install();
            cityAdvancements.startSyncTask();
        });

        // ── NPCs génériques ───────────────────────────────────────
        List<QuestGUI>    questGUIs    = new ArrayList<>();
        List<QuestConfig> questConfigs = new ArrayList<>();

        for (CityNPC npcType : CityNPC.values()) {
            if (npcType == CityNPC.MAYOR) continue;

            VillagerConfig config      = new VillagerConfig(this, npcType.skinId);
            // Entrepôt agrandi selon le niveau de son atelier (phase 2 : inspection des constructions)
            config.setCapacityMultiplier(() -> buildingInspector.capacityMultiplier(npcType));
            market.register(npcType, config);
            VillagerGUI    gui         = new VillagerGUI(npcType, config,
                    npcDataManager, npcManager, playerDataManager, market, this);
            gui.setIntegrations(modIntegrations); // habitant lié à un mod absent : pas de menu
            gui.setInspector(buildingInspector);
            QuestConfig    questConfig = new QuestConfig(this, npcType.skinId, market);
            questConfigs.add(questConfig);

            // Crée le GUI perso seulement si le NPC a des quêtes perso déclarées
            NPCPersonalQuestGUI personalGUI = npcType.hasPersonalQuests()
                    ? new NPCPersonalQuestGUI(npcType, npcDataManager, personalQuestManager)
                    : null;

            QuestGUI questGUI = new QuestGUI(npcType, questConfig,
                    questManager, npcDataManager, personalGUI);

            questGUIs.add(questGUI);

            VillagerListener villagerListener = new VillagerListener(npcType, gui, npcManager,
                    npcDataManager, economy, cityManager, introManager,
                    questGUI, this, notificationManager,
                    buildingManager, playerDataManager, market);
            villagerListener.setInspector(buildingInspector); // logis : atelier conforme exigé
            getServer().getPluginManager().registerEvents(villagerListener, this);
            com.citycore.ui.screens.ArtisanScreens screens = new com.citycore.ui.screens.ArtisanScreens(uiKit,
                    npcType, gui, npcDataManager, npcManager, playerDataManager, market, villagerListener, questGUI);
            screens.setInhabitants(inhabitants); // gestion → Actions → La garde (chez Roland)
            // Actions → Employés et annexe de travail (création : nom, puis zone au bâton)
            screens.setJobs(jobs, (p, artisan) -> buildingCreation.askName(p, BuildingType.ANNEXE, artisan));
            gui.setThemed(screens);
            questScreens.register(questGUI, gui);
        }

        // ── Consommation des stocks + contrôle anti-arbitrage ────
        market.startConsumptionTask();
        EconomyValidator validator = new EconomyValidator(market);
        Supplier<List<String>> economyCheck = () -> {
            Map<Material, Double> questRewards = new EnumMap<>(Material.class);
            List<String> warnings = new ArrayList<>();
            for (QuestConfig qc : questConfigs) {
                qc.unitRewards().forEach((m, v) -> questRewards.merge(m, v, Math::max));
                qc.unpricedEntries().forEach(e -> warnings.add("Quête sans valeur : " + e));
            }
            warnings.addAll(validator.run(questRewards));
            return warnings;
        };
        // 1 tick plus tard : toutes les recettes (datapacks compris) sont chargées
        Bukkit.getScheduler().runTask(this, () -> {
            List<String> warnings = economyCheck.get();
            if (warnings.isEmpty()) {
                getLogger().info("✅ Économie : aucun arbitrage achat → craft → revente détecté.");
            } else {
                logGrouped(warnings);
            }
            PersonalQuestCatalog.unknownAdvancements(modIntegrations::namespaceActive).forEach(k -> getLogger().warning(
                    "⚠ [Quêtes] Succès introuvable sur ce serveur : " + k + " (quête personnelle impossible)"));
        });

        // ── HUD Quêtes NPC ────────────────────────────────────────
        QuestHUD questHUD = new QuestHUD(this, questManager, npcDataManager, questGUIs);
        questHUD.setUi(uiKit); // panneau illustré « Quêtes actives » à gauche de l'écran
        questHUD.setIntegrations(modIntegrations);
        questHUD.startUpdating();

        // ── QuestListener — instancié AVANT SummitListener ───────
        QuestListener questListener = new QuestListener(
                questGUIs, questManager, personalQuestManager,
                npcDataManager, economy, this, market, questHUD,
                npcManager, notificationManager);

        getServer().getPluginManager().registerEvents(questListener, this);
        questScreens.setActions(questListener);
        questListener.startRidingTicker(); // quêtes de vol (monture de mod sans événement de déplacement)

        // ── Journal de quêtes (/city quests) : suivi et récits des habitants ──
        com.citycore.quest.personal.StoryJournal storyJournal = new com.citycore.quest.personal.StoryJournal(
                npcDataManager, personalQuestManager, modIntegrations, questGUIs);
        CityQuestSelectionGUI questSelectionGUI = new CityQuestSelectionGUI(questHUD, npcManager, this);
        questSelectionGUI.setJournal(storyJournal);
        getServer().getPluginManager().registerEvents(
                new CityQuestSelectionListener(questSelectionGUI), this);

        // ── Chunks ────────────────────────────────────────────────
        ClaimProtectionListener claimProtection = new ClaimProtectionListener(cityManager);
        claimProtection.setSiegeAttacker(siege::woundsPlayers); // siège : les assaillants peuvent blesser les joueurs
        getServer().getPluginManager().registerEvents(claimProtection, this);

        // ── Commandes ────────────────────────────────────────────
        var cityCmd = getCommand("city");
        CityCommand cityCommand = new CityCommand(cityManager, npcManager, this,
                npcDataManager, questHUD, buildingManager, buildingSession,
                buildingGUI, buildingBorderTask, notificationManager,
                playerDataManager, questSelectionGUI);
        cityCommand.setEconomyCheck(economyCheck);
        cityCommand.setCityQuestManager(cityQuestManager);
        cityCommand.setCityAdvancements(cityAdvancements);
        cityCommand.setUiPack(uiPack);
        // Journal de quêtes illustré (/city quests : onglets Suivi et Récits) ; menu classique sans le pack
        com.citycore.ui.screens.QuestTrackingScreen questJournal =
                new com.citycore.ui.screens.QuestTrackingScreen(uiKit, questHUD, npcManager, storyJournal);
        questJournal.setClassic(questSelectionGUI::open, questSelectionGUI::openStories);
        cityCommand.setQuestTracking(questJournal);
        cityCommand.setBuildingCreation(buildingCreation);
        cityCommand.setBuildingInspector(buildingInspector);
        cityCommand.setInhabitants(inhabitants);
        cityCommand.setSiege(siege, calendar);
        cityCommand.setNeeds(needs);
        cityCommand.setJobs(jobs);
        cityCommand.setMapExport(mapExport);
        // Phase 0 du plan « Valmont vivante » : prototype temporaire (/city proto)
        prototype = new com.citycore.debug.NpcPrototype(this, cityManager);
        getServer().getPluginManager().registerEvents(prototype, this);
        cityCommand.setPrototype(prototype);
        cityCmd.setExecutor(cityCommand);
        CityTabCompleter cityTabCompleter = new CityTabCompleter(cityManager, buildingManager, npcManager);
        cityTabCompleter.setBuildingCreation(buildingCreation);
        cityTabCompleter.setInhabitants(inhabitants);
        cityCmd.setTabCompleter(cityTabCompleter);

        // ── Summit — instancié APRÈS QuestListener ────────────────
        SummitListener summitListener = new SummitListener(this, questListener);
        getServer().getPluginManager().registerEvents(summitListener, this);

        // Dragon
        getServer().getPluginManager().registerEvents(new DragonListener(this), this);

        // Pirate
        getServer().getPluginManager().registerEvents(new PirateListener(this), this);

        // ── Listeners globaux ─────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new ChunkListener(cityManager, npcManager, cityHUD, this, findNpcQuestManager), this);

        // ── Restauration NPCs ─────────────────────────────────────
        Bukkit.getScheduler().runTaskLater(this, () -> npcManager.restoreNPCs(), 20L);

        NPCCityArrivalTask arrivalTask = new NPCCityArrivalTask(npcManager, npcDataManager, cityManager,
                this, buildingManager, notificationManager, findNpcQuestManager);
        arrivalTask.setAtelierRequest(buildingInspector::requestSentence); // dialogue d'arrivée : ses exigences
        arrivalTask.start();
        npcManager.startFollowSupervisor();

        // ── WorldEdit ─────────────────────────────────────────────
        getServer().getMessenger().registerOutgoingPluginChannel(this, "worldedit:cui");

        getLogger().info("CityCore enabled ✅");
    }

    @Override
    public void onDisable() {
        if (prototype != null) prototype.clear(); // PNJ et zombies du prototype : rien ne doit rester
        if (siege != null) siege.shutdown();             // assaillants retirés : rien ne reste dans le monde
        if (chunkLoader != null) chunkLoader.shutdown(); // chunks de la ville rendus
        if (inhabitants != null) inhabitants.shutdown(); // recréés depuis la base au prochain démarrage
        if (cityHUD != null) cityHUD.stop();
        if (uiPack != null) uiPack.stop();
        databaseManager.closeDatabase();
        getLogger().info("CityCore disabled");
    }

    /**
     * Alertes économiques regroupées par type (3 exemples par type au démarrage) :
     * une config incohérente ne noie pas la console. Détail complet : /city eco check.
     */
    private void logGrouped(List<String> warnings) {
        Map<String, List<String>> byType = new java.util.LinkedHashMap<>();
        for (String w : warnings) {
            int sep = w.indexOf(" : ");
            byType.computeIfAbsent(sep > 0 ? w.substring(0, sep) : w, k -> new ArrayList<>()).add(w);
        }
        getLogger().warning("⚠ [Économie] " + warnings.size() + " alerte(s) — détail : /city eco check");
        byType.forEach((type, list) -> {
            list.stream().limit(3).forEach(w -> getLogger().warning("⚠ [Économie] " + w));
            if (list.size() > 3) {
                getLogger().warning("⚠ [Économie] … et " + (list.size() - 3) + " autre(s) « " + type + " »");
            }
        });
    }

    private Economy setupEconomy() {
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public Economy         getEconomy()         { return economy; }
}