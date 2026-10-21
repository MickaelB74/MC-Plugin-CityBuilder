package com.citycore.command;

import com.citycore.building.*;
import com.citycore.city.City;
import com.citycore.city.ParcelActions;
import com.citycore.npc.*;
import com.citycore.npc.villager.VillagerGUI;
import com.citycore.player.PlayerDataManager;
import com.citycore.quest.QuestHUD;
import com.citycore.chunk.ChunkParticleTask;
import com.citycore.city.CityManager;
import com.citycore.util.TypewriterUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.citycore.progress.CityAdvancements;
import com.citycore.quest.city.CityQuestManager;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.bukkit.Bukkit.getServer;

public class CityCommand implements CommandExecutor {

    private final CityManager            cityManager;
    private final JavaPlugin             plugin;
    private       Economy                economy;
    private final NPCManager             npcManager;
    private final NPCDataManager         npcDataManager;
    private final QuestHUD               questHUD;
    private final BuildingManager        buildingManager;
    private final BuildingSession        buildingSession;
    private final BuildingGUI            buildingGUI;
    private final BuildingBorderTask     buildingBorderTask;
    private final NPCNotificationManager notificationManager;
    private final PlayerDataManager      playerDataManager;
    private final CityQuestSelectionGUI  questSelectionGUI;
    private Supplier<List<String>>       economyCheck;
    private CityQuestManager             cityQuestManager;
    private CityAdvancements             cityAdvancements;
    private com.citycore.ui.UiPack       uiPack;
    private com.citycore.ui.screens.QuestTrackingScreen questTracking;
    /** Nom de la ville : Valmont, rebâtie sur ses cendres (voir CityTier). */
    public static final String CITY_NAME = "Valmont";

    public void setUiPack(com.citycore.ui.UiPack uiPack) {
        this.uiPack = uiPack;
    }

    /** Création des bâtiments : choix du type, sélection, changement de type. */
    private BuildingCreation buildingCreation;

    public void setBuildingCreation(BuildingCreation buildingCreation) {
        this.buildingCreation = buildingCreation;
    }

    /** Habitants de la ville (phase 3) : /city habitants. */
    private com.citycore.inhabitant.InhabitantManager inhabitants;

    public void setInhabitants(com.citycore.inhabitant.InhabitantManager inhabitants) {
        this.inhabitants = inhabitants;
    }

    /** Place libre près du joueur (à 2-3 blocs, au même niveau), sinon sa propre position. */
    private static Location testSpot(Player player) {
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            Location at = player.getLocation().clone().add(rnd.nextInt(-3, 4), 0, rnd.nextInt(-3, 4));
            if (at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable()
                    && at.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                return at.getBlock().getLocation().add(0.5, 0, 0.5);
            }
        }
        return player.getLocation();
    }

    private com.citycore.siege.SiegeManager  siege;
    private com.citycore.siege.EventCalendar calendar;
    private com.citycore.needs.NeedsManager  needs;
    private com.citycore.jobs.JobsManager    jobs;

    /** Emplois des habitants chez les artisans. */
    public void setJobs(com.citycore.jobs.JobsManager jobs) {
        this.jobs = jobs;
    }

    /** Mods du serveur hybride (/city mods, admin). */
    private com.citycore.integration.ModIntegrations modIntegrations;

    public void setModIntegrations(com.citycore.integration.ModIntegrations modIntegrations) {
        this.modIntegrations = modIntegrations;
    }

    /** Menus des habitants, ouverts sans atelier par /city npc <type> menu (tests, admin). */
    private java.util.Map<CityNPC, VillagerGUI> npcMenus = java.util.Map.of();

    public void setNpcMenus(java.util.Map<CityNPC, VillagerGUI> npcMenus) {
        this.npcMenus = npcMenus;
    }

    /** Parcelles de la ville sur les cartes JourneyMap (/city carte, admin). */
    private com.citycore.city.CityMapExport mapExport;

    public void setMapExport(com.citycore.city.CityMapExport mapExport) {
        this.mapExport = mapExport;
    }

    /** Besoins et satisfaction des habitants (phase 6). */
    public void setNeeds(com.citycore.needs.NeedsManager needs) {
        this.needs = needs;
    }

    /** Sièges et calendrier des événements (phase 4). */
    public void setSiege(com.citycore.siege.SiegeManager siege, com.citycore.siege.EventCalendar calendar) {
        this.siege    = siege;
        this.calendar = calendar;
    }

    /** Inspection des constructions (phase 2) : /city build inspect, contrôle de /city build assign. */
    private com.citycore.building.BuildingInspector buildingInspector;

    public void setBuildingInspector(com.citycore.building.BuildingInspector buildingInspector) {
        this.buildingInspector = buildingInspector;
    }

    /** Raids (plan « Raids ») : /city raid. */
    private com.citycore.raid.RaidCommands raids;

    public void setRaids(com.citycore.raid.RaidCommands raids) {
        this.raids = raids;
    }

    /** Prototype de la phase 0 (/city proto) : PNJ blessables, foule, combat. Temporaire. */
    private com.citycore.debug.NpcPrototype prototype;

    public void setPrototype(com.citycore.debug.NpcPrototype prototype) {
        this.prototype = prototype;
    }

    /** Menu illustré « Quêtes suivies » (/city quests). */
    public void setQuestTracking(com.citycore.ui.screens.QuestTrackingScreen questTracking) {
        this.questTracking = questTracking;
    }

    /** Où sont-ils ? (/city locate) : carte de Valmont et position des personnages. */
    private com.citycore.ui.screens.LocateScreens locate;

    public void setLocate(com.citycore.ui.screens.LocateScreens locate) {
        this.locate = locate;
    }

    /** Arbre de progression (/city progression). */
    public void setCityAdvancements(CityAdvancements cityAdvancements) {
        this.cityAdvancements = cityAdvancements;
    }

    /** Contrôle anti-arbitrage (/city eco check). */
    public void setEconomyCheck(Supplier<List<String>> economyCheck) {
        this.economyCheck = economyCheck;
    }

    public void setCityQuestManager(CityQuestManager cityQuestManager) {
        this.cityQuestManager = cityQuestManager;
    }

    public CityCommand(CityManager cityManager, NPCManager npcManager,
                       JavaPlugin plugin, NPCDataManager npcDataManager,
                       QuestHUD questHUD, BuildingManager buildingManager,
                       BuildingSession buildingSession, BuildingGUI buildingGUI,
                       BuildingBorderTask buildingBorderTask,
                       NPCNotificationManager notificationManager,
                       PlayerDataManager playerDataManager, CityQuestSelectionGUI questSelectionGUI) {
        this.cityManager         = cityManager;
        this.npcManager          = npcManager;
        this.plugin              = plugin;
        this.npcDataManager      = npcDataManager;
        this.questHUD            = questHUD;
        this.buildingManager     = buildingManager;
        this.buildingSession     = buildingSession;
        this.buildingGUI         = buildingGUI;
        this.buildingBorderTask  = buildingBorderTask;
        this.notificationManager = notificationManager;
        this.playerDataManager   = playerDataManager;
        this.questSelectionGUI   = questSelectionGUI;
        setupEconomy();
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
        else plugin.getLogger().warning("Vault/Economy introuvable.");
    }

    /* =========================
       DROITS (voir CityPermissions et plugin.yml)
       Un joueur passe par les habitants pour tout ce qu'ils savent faire : la commande ne lui
       laisse que les consultations et le confort ; les administrateurs ont tout.
       ========================= */

    /** Où former et renvoyer les gardes (écran illustré de Roland). */
    private static final String GUARD_HINT = "Parlez à §9Roland §7: cliquez sur son portrait, puis « Actions » → « La garde ».";

    /** Le joueur voit-il les écrans illustrés (sans eux, certaines commandes restent son seul accès) ? */
    private boolean illustrated(Player player) {
        return player != null && uiPack != null && uiPack.isReady(player);
    }

    /**
     * /city build pour un joueur sans citycore.admin.batiments : inspection et bordures, le bâton
     * de création (skip) ; new et type seulement depuis les boutons du chat qu'Alderic vient de
     * proposer (menus classiques). Le reste se fait chez Alderic ou chez l'artisan.
     * @return true si l'action est permise (sinon le joueur est renvoyé vers le bon PNJ)
     */
    private boolean playerBuildAction(Player player, String[] args) {
        String action = args.length < 2 ? "" : args[1].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "inspect", "inspecter", "show", "skip" -> { return true; }
            case "new" -> {
                if (buildingCreation != null && buildingCreation.hasChatChoice(player, null)) return true;
                CityPermissions.deny(player, "Parlez à §6Alderic §7(Maire) → « Bâtiments » → « Ajouter un bâtiment ».");
            }
            case "type" -> {
                if (args.length >= 3 && buildingCreation != null && buildingCreation.hasChatChoice(player, args[2])) return true;
                CityPermissions.deny(player, "Parlez à §6Alderic §7(Maire) → « Bâtiments » → §fclic droit §7sur le bâtiment.");
            }
            case "remove" -> CityPermissions.deny(player,
                    "Parlez à §6Alderic §7(Maire) → « Bâtiments » → « Supprimer un bâtiment ».");
            case "assign" -> CityPermissions.deny(player,
                    "Parlez à l'artisan lui-même → « Choisir son logis » §8(« Chercher un bâtiment » en menus classiques)§7.");
            case "hauteur", "height" -> {
                player.sendMessage("§c❌ Réglage d'administration : la zone en hauteur d'un bâtiment se règle seule"
                        + " à sa création, et la hauteur construite se mesure à chaque inspection.");
            }
            case "" -> CityPermissions.deny(player, "Les bâtiments se gèrent chez §6Alderic §7(Maire) → « Bâtiments »."
                    + "\n§7En commande : §f/city build inspect [nom] §7· §f/city build show");
            default -> player.sendMessage("§cUsage : /city " + CitySubCommand.BUILD.usageFor(player));
        }
        return false;
    }

    /** Message d'une action réservée à un joueur (position, menu…) demandée depuis la console. */
    private static boolean inGameOnly(CommandSender sender, Player player) {
        if (player != null) return false;
        sender.sendMessage("§cEn jeu seulement.");
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        CitySubCommand sub = CitySubCommand.fromLabel(args[0]);
        if (sub == null) { sendHelp(sender); return true; }

        // ── Droit d'utiliser la sous-commande : sinon, le PNJ qui s'en charge ──
        if (!CityPermissions.canUse(sender, sub)) {
            CityPermissions.deny(sender, sub.rights.npcHint());
            return true;
        }
        // ── Console : seulement les sous-commandes sans position ni menu ──
        Player player = sender instanceof Player p ? p : null;
        if (player == null && !sub.rights.console()) {
            sender.sendMessage("Commande joueur uniquement. Depuis la console : " + String.join(", ",
                    Arrays.stream(CitySubCommand.values()).filter(c -> c.rights.console()).map(c -> c.label).toList()) + ".");
            return true;
        }
        // Actions d'administration et de test de cette sous-commande (nœud citycore.admin.*)
        boolean admin = CityPermissions.isAdmin(sender, sub);

        try {
            switch (sub) {

                // ── /city create ─────────────────────────────────
                case CREATE -> {
                    if (cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ La ville existe déjà.");
                        return true;
                    }
                    // La ville est Valmont, qui renaît de ses cendres : pas de nom à saisir
                    String cityName = CITY_NAME;
                    cityManager.initializeCity(cityName);
                    cityManager.claimChunk(player.getLocation().getChunk());
                    npcManager.spawnMayor(player);
                    player.sendMessage("§6🏰 §e" + cityName + " §6renaît de ses cendres !");
                    player.sendMessage("§a📍 Chunk de départ claim.");
                    player.sendMessage("§7👴 Le §6Maire §7est apparu devant vous.");
                }

                // ── /city claim (admin ; joueurs : barre « Parcelles » d'Alderic) ──
                case CLAIM -> ParcelActions.claimHere(cityManager, player);

                // ── /city unclaim (admin ; joueurs : barre « Parcelles » d'Alderic) ──
                case UNCLAIM -> ParcelActions.unclaimHere(cityManager, buildingManager, player);

                // ── /city expand (admin ; joueurs : « Agrandir la ville » chez Alderic) ──
                case EXPAND -> {
                    if (!cityManager.isCityInitialized()) {
                        sender.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    int price   = cityManager.getNextExpandPrice();
                    int balance = cityManager.getCityCoins();
                    sender.sendMessage("§7Prix : §6" + price
                            + " coins §7· Caisse : §6" + balance + " coins");
                    CityManager.ExpandResult result = cityManager.expandMaxChunks();
                    if (result.success()) {
                        sender.sendMessage("§a✅ Capacité étendue ! §7Max : §f"
                                + result.newMaxChunks());
                        sender.sendMessage("§7Caisse restante : §6"
                                + result.newBalance() + " coins");
                    } else {
                        sender.sendMessage("§c❌ Fonds insuffisants. Il manque §f"
                                + (price - balance) + " coins.");
                    }
                }

                // ── /city deposit ────────────────────────────────
                // ── /city niveau [monter|descendre|<n>] : chapitre de la ville (tests) ──
                case NIVEAU -> {
                    if (!cityManager.isCityInitialized() || cityQuestManager == null) {
                        sender.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    com.citycore.quest.city.CityTier[] tiers = com.citycore.quest.city.CityTier.values();
                    int max = tiers[tiers.length - 1].level;
                    int current = cityQuestManager.getCityLevel();
                    String how = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
                    int wanted;
                    switch (how) {
                        case "" -> {
                            com.citycore.quest.city.CityTier t = com.citycore.quest.city.CityTier.fromLevel(current);
                            sender.sendMessage("§6🏛 Niveau de la ville : §f" + current + " §7— " + t.tierName + " §7(" + t.chapter + ")");
                            sender.sendMessage("§7Usage : §f/city niveau <monter|descendre|1-" + max + ">");
                            return true;
                        }
                        case "monter", "up", "+" -> wanted = current + 1;
                        case "descendre", "down", "-" -> wanted = current - 1;
                        default -> {
                            try {
                                wanted = Integer.parseInt(how);
                            } catch (NumberFormatException e) {
                                sender.sendMessage("§cUsage : /city niveau <monter|descendre|1-" + max + ">");
                                return true;
                            }
                        }
                    }
                    wanted = Math.max(1, Math.min(max, wanted));
                    if (wanted == current) {
                        sender.sendMessage("§7La ville est déjà au niveau " + current + (current == max ? " (le plus haut)."
                                : current == 1 ? " (le premier)." : "."));
                        return true;
                    }
                    cityQuestManager.setCityLevel(wanted);
                    cityQuestManager.syncCurrentTier();
                    // Écrans des quêtes de la cité ouverts : contenu périmé, fermés
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (com.citycore.ui.screens.MayorScreens.isCityQuestScreen(p.getOpenInventory().getTopInventory().getHolder())) {
                            p.closeInventory();
                        }
                    }
                    com.citycore.quest.city.CityTier t = com.citycore.quest.city.CityTier.fromLevel(wanted);
                    sender.sendMessage("§a✅ Niveau de la ville : §f" + current + " → " + wanted + " §7— " + t.tierName
                            + " §7(" + t.chapter + "). §8Sans charte payée ni parcelles offertes.");
                    plugin.getLogger().info("Niveau de la ville changé par " + sender.getName() + " : " + current + " → " + wanted + ".");
                }

                case DEPOSIT -> {
                    if (!cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    if (economy == null) {
                        player.sendMessage("§c❌ Vault non disponible.");
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage("§cUsage : /city " + CitySubCommand.DEPOSIT.usage);
                        return true;
                    }
                    int amount;
                    try {
                        amount = Integer.parseInt(args[1]);
                        if (amount <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c❌ Montant invalide.");
                        return true;
                    }
                    if (!economy.has(player, amount)) {
                        player.sendMessage("§c❌ Solde insuffisant. §7Vous avez : §6"
                                + (int) economy.getBalance(player));
                        return true;
                    }
                    economy.withdrawPlayer(player, amount);
                    int newBalance = cityManager.addCityCoins(amount);
                    if (cityQuestManager != null) cityQuestManager.onCoinsDeposited(amount);
                    player.sendMessage("§a✅ §6" + amount
                            + " coins §adéposés dans la caisse.");
                    player.sendMessage("§7Caisse : §6" + newBalance
                            + " §7· Solde : §6" + (int) economy.getBalance(player));
                }

                // ── /city map ────────────────────────────────────
                case MAP -> {
                    if (!cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    new ChunkParticleTask(plugin, cityManager, player).runForSeconds(5);
                    player.sendMessage("§b🗺 Bordures affichées pendant §f5 secondes§b.");
                }

                // ── /city info ───────────────────────────────────
                case INFO -> {
                    City city = cityManager.getCity();
                    if (city == null) {
                        sender.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    sender.sendMessage("§8§m--------------------");
                    sender.sendMessage("§6 " + city.getName());
                    sender.sendMessage("§8§m--------------------");
                    sender.sendMessage("§eNiveau  : §f" + city.getLevel());
                    sender.sendMessage("§eCaisse  : §6" + city.getCoins() + " coins");
                    sender.sendMessage("§eChunks  : §f" + city.getClaimedChunks()
                            + " §7/ §f" + city.getMaxChunks());
                    sender.sendMessage("§eExpand  : §6" + cityManager.getNextExpandPrice()
                            + " coins §7pour +1 slot");
                    sender.sendMessage("§8§m--------------------");
                }

                // ── /city quests ─────────────────────────────────
                case QUESTS -> {
                    // /city quests toggle [visible|accroche|masque] : fait défiler (ou choisit) l'affichage
                    // du suivi, gardé dans les préférences du joueur ; /city quests recits : onglet des
                    // récits ; sinon onglet du suivi (menu illustré, ou classique sans le pack)
                    String option = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
                    switch (option) {
                        case "toggle" -> {
                            QuestHUD.Mode chosen = args.length > 2 ? QuestHUD.Mode.parse(args[2]) : null;
                            if (args.length > 2 && chosen == null) {
                                player.sendMessage("§c❌ Affichage inconnu : §fvisible§c, §faccroche §cou §fmasque§c.");
                                return true;
                            }
                            questHUD.toggle(player, chosen);
                        }
                        case "recits", "récits" -> {
                            if (questTracking == null || !questTracking.openStories(player)) questSelectionGUI.openStories(player);
                        }
                        default -> {
                            if (questTracking == null || !questTracking.open(player)) questSelectionGUI.open(player);
                        }
                    }
                }

                // ── /city locate [personnage] : où sont-ils ? (carte illustrée, sinon texte) ──
                case LOCATE -> {
                    if (locate == null) return true;
                    com.citycore.npc.CityNPC focus = null;
                    if (args.length > 1) {
                        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                        focus = locate.find(name);
                        if (focus == null) {
                            player.sendMessage("§c❌ Personnage inconnu : §f" + name);
                            return true;
                        }
                        if (!locate.discovered(focus)) {
                            player.sendMessage("§7🔒 " + focus.displayName.replaceAll("§.", "")
                                    + " §7n'est pas encore arrivé à Valmont.");
                            focus = null;
                        }
                    }
                    if (!locate.open(player, focus)) locate.report(player);
                }

                // ── /city progression ────────────────────────────
                case PROGRESSION -> {
                    // La vue d'ensemble est l'onglet de progrès Minecraft : on le présélectionne
                    // et on rappelle la touche (affichée selon les réglages du joueur)
                    if (cityAdvancements != null) cityAdvancements.selectTab(player);
                    player.sendMessage(net.kyori.adventure.text.Component.text("📜 Progression de la ville : ouvrez le menu Progrès (", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                            .append(net.kyori.adventure.text.Component.keybind("key.advancements", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                            .append(net.kyori.adventure.text.Component.text("), onglet « La Renaissance de Valmont ».", net.kyori.adventure.text.format.NamedTextColor.GOLD)));
                }

                // ── /city eco check ──────────────────────────────
                // ── /city ui [valmont|classique] ─────────────────
                case UI -> {
                    if (uiPack == null) return true;
                    String mode = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
                    boolean classic = switch (mode) {
                        case "classique", "classic" -> true;
                        case "valmont", "illustre", "illustré" -> false;
                        default -> !uiPack.prefersClassic(player); // bascule
                    };
                    uiPack.setClassic(player, classic);
                    if (classic) {
                        player.sendMessage("§7Menus des habitants : §fclassiques§7. §8(/city ui valmont pour revenir)");
                    } else if (!uiPack.enabled()) {
                        player.sendMessage("§cLe pack d'interface n'est pas disponible sur ce serveur (voir la console).");
                    } else {
                        player.sendMessage("§6Menus des habitants : §fillustrés (Valmont)§6.");
                        if (!uiPack.isLoaded(player)) uiPack.send(player);
                    }
                }

                case ECO -> {
                    if (economyCheck == null) return true;
                    List<String> warnings = economyCheck.get();
                    if (warnings.isEmpty()) {
                        sender.sendMessage("§a✅ Économie saine : aucun arbitrage achat → craft → revente.");
                    } else {
                        sender.sendMessage("§6⚠ " + warnings.size() + " alerte(s) économiques :");
                        warnings.stream().limit(15).forEach(w -> sender.sendMessage("§7- §e" + w));
                        if (warnings.size() > 15) sender.sendMessage("§8… voir la console pour la suite.");
                        warnings.forEach(w -> plugin.getLogger().warning("⚠ [Économie] " + w));
                    }
                    // Équilibrage (phase 6) : revenus de l'impôt face aux chartes restantes
                    if (needs != null && inhabitants != null && cityQuestManager != null) {
                        needs.economyLines(cityQuestManager.getCityLevel(), inhabitants.taxPerInhabitant())
                                .forEach(sender::sendMessage);
                    }
                }

                // ── /city habitants (phase 3) ────────────────────
                // Liste et besoins pour tous (comme l'écran « Population » d'Alderic) ; le reste : tests (admin)
                case HABITANTS -> {
                    if (inhabitants == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "list";
                    if (!admin && !List.of("list", "liste", "besoins", "satisfaction").contains(action)) {
                        CityPermissions.deny(sender, null);
                        return true;
                    }
                    switch (action) {
                        case "list", "liste" -> {
                            if (inGameOnly(sender, player)) return true;
                            inhabitants.list(player);
                        }
                        case "arrivee", "arrivée" -> sender.sendMessage(inhabitants.arrive()
                                ? "§a✅ Un habitant arrive." : "§7Aucun lit libre dans une habitation conforme.");
                        case "kill", "tuer" -> {
                            if (args.length < 3) {
                                sender.sendMessage("§cUsage : /city habitants kill <nom>");
                                return true;
                            }
                            String target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                            if (!inhabitants.kill(target)) sender.sendMessage("§c❌ Habitant §f" + target + " §cintrouvable.");
                        }
                        case "impot", "impôt" -> {
                            int paid = inhabitants.payTax();
                            if (paid == 0) sender.sendMessage("§7Aucun habitant : aucun impôt versé.");
                        }
                        // Besoins (phase 6) : bilan pour tous ; « aube » (admin) déclenche repas, soins et impôt
                        case "besoins", "satisfaction" -> {
                            if (needs != null) needs.report().forEach(sender::sendMessage);
                        }
                        case "aube" -> {
                            int paid = inhabitants.dawn();
                            if (paid == 0) sender.sendMessage("§7Aube passée : aucun impôt versé.");
                        }
                        case "deuil" -> sender.sendMessage("§a✅ " + inhabitants.endAllMourning()
                                + " lit(s) libéré(s) : de nouveaux habitants pourront arriver.");
                        case "recharger", "reload" -> {
                            inhabitants.loadSettings();
                            if (needs != null) needs.config().reload();
                            sender.sendMessage("§a✅ Réglages des habitants et de leurs besoins relus (sections habitants et besoins).");
                        }
                        default -> sender.sendMessage("§cUsage : /city " + sub.usageFor(sender));
                    }
                }

                // ── /city siege (phase 4) ────────────────────────
                // État pour tous ; lancer, arrêter, vague suivante : admin (citycore.admin.evenements)
                case SIEGE -> {
                    if (siege == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "etat";
                    if (!admin && !List.of("etat", "état", "status").contains(action)) {
                        CityPermissions.deny(sender, null);
                        return true;
                    }
                    switch (action) {
                        case "etat", "état", "status" -> siege.status().forEach(sender::sendMessage);
                        case "start", "lancer" -> {
                            // /city siege start [palier] [monstres|banniere] (dans n'importe quel ordre)
                            Integer tier = null;
                            com.citycore.siege.SiegeConfig.Kind kind = null;
                            for (int i = 2; i < args.length; i++) {
                                com.citycore.siege.SiegeConfig.Kind k = com.citycore.siege.SiegeConfig.Kind.parse(args[i]);
                                if (k != null) { kind = k; continue; }
                                try {
                                    tier = Integer.parseInt(args[i]);
                                } catch (NumberFormatException e) {
                                    sender.sendMessage("§cUsage : /city siege start [palier 1-10] [monstres|banniere]");
                                    return true;
                                }
                            }
                            String err = siege.begin(tier, true, kind);
                            if (err != null) sender.sendMessage("§c❌ " + err);
                        }
                        case "stop", "arreter", "arrêter" -> {
                            if (!siege.stop()) sender.sendMessage("§7Aucun siège en cours.");
                        }
                        case "next", "suivante" -> {
                            if (!siege.skipWave()) sender.sendMessage("§7Aucun siège en cours.");
                        }
                        default -> sender.sendMessage("§cUsage : /city siege [start [palier]|stop|next]");
                    }
                }

                // ── /city event (phase 4) ────────────────────────
                // Planning pour tous ; lancer, arrêter, recharger : admin (citycore.admin.evenements)
                case EVENT -> {
                    if (calendar == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "list";
                    if (!admin && !List.of("list", "liste").contains(action)) {
                        CityPermissions.deny(sender, null);
                        return true;
                    }
                    switch (action) {
                        case "list", "liste" -> calendar.listing().forEach(sender::sendMessage);
                        case "start", "lancer" -> {
                            com.citycore.siege.EventCalendar.Event event = calendar.event(args.length >= 3 ? args[2] : "siege");
                            if (event == null) {
                                sender.sendMessage("§c❌ Type inconnu. Types : " + String.join(", ", calendar.types()));
                                return true;
                            }
                            String err = event.start();
                            if (err != null) sender.sendMessage("§c❌ " + err);
                        }
                        case "stop", "arreter", "arrêter" -> {
                            boolean stopped = false;
                            for (String type : calendar.types()) stopped |= calendar.event(type).stop();
                            if (!stopped) sender.sendMessage("§7Aucun événement en cours.");
                        }
                        case "recharger", "reload" -> {
                            calendar.reload();
                            siege.reload();
                            sender.sendMessage("§a✅ Calendrier et réglages des sièges relus (sections evenements et sieges).");
                        }
                        default -> sender.sendMessage("§cUsage : /city event [list|start <type>|stop|recharger]");
                    }
                }

                // ── /city emplois ────────────────────────────────
                // Bilan pour tous (embauches : chez l'artisan, Actions → Employés) ; produire, recharger : admin
                case EMPLOIS -> {
                    if (jobs == null || inhabitants == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "list";
                    if (!admin && !List.of("list", "liste").contains(action)) {
                        CityPermissions.deny(sender, null);
                        return true;
                    }
                    switch (action) {
                        case "list", "liste" -> jobs.report().forEach(sender::sendMessage);
                        case "produire" -> sender.sendMessage("§a✅ Cycles de travail terminés : §f"
                                + inhabitants.finishWorkNow() + " §aobjet(s) produit(s) dans les entrepôts.");
                        case "recharger", "reload" -> {
                            jobs.config().reload();
                            sender.sendMessage("§a✅ Réglages des emplois relus (section emplois de config.yml).");
                        }
                        default -> sender.sendMessage("§cUsage : /city emplois [list|produire|recharger]");
                    }
                }

                // ── /city mods : mods présents ou absents, personnages concernés (admin, console aussi) ──
                case MODS -> {
                    if (modIntegrations != null) modIntegrations.report().forEach(sender::sendMessage);
                }

                // ── /city carte : parcelles sur les cartes JourneyMap (admin, console aussi) ──
                case CARTE -> {
                    if (mapExport == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "etat";
                    switch (action) {
                        case "etat", "état" -> mapExport.report().forEach(sender::sendMessage);
                        case "recharger", "reload" -> {
                            mapExport.reload();
                            mapExport.export();
                            sender.sendMessage("§a✅ Réglages de la carte relus (section carte de config.yml) ; "
                                    + "les cartes suivent dans les 2 s.");
                        }
                        default -> sender.sendMessage("§cUsage : /city " + CitySubCommand.CARTE.usage);
                    }
                }

                // ── /city garde (phase 5) ────────────────────────
                // Liste pour tous. Former et renvoyer se font chez Roland (écran « La garde ») : la commande
                // ne reste ouverte aux joueurs que sans les écrans illustrés (Roland n'a pas d'écran classique
                // de la garde). Fin, kill et test sont des actions de test (admin).
                case GARDE -> {
                    if (inhabitants == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "list";
                    boolean rolandMenu = List.of("former", "train", "renvoyer", "dismiss").contains(action);
                    if (!admin && !List.of("list", "liste").contains(action)
                            && (!rolandMenu || illustrated(player))) {
                        CityPermissions.deny(sender, rolandMenu ? GUARD_HINT : null);
                        return true;
                    }
                    switch (action) {
                        case "list", "liste" -> {
                            if (inGameOnly(sender, player)) return true;
                            inhabitants.listGuard(player);
                        }
                        case "former", "train" -> {
                            if (args.length < 4) {
                                sender.sendMessage("§cUsage : /city garde former <habitant> <rôle>");
                                return true;
                            }
                            // Le rôle est le dernier mot, le nom peut en contenir plusieurs (« Jehan 2 »)
                            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1));
                            com.citycore.inhabitant.InhabitantManager.Inhabitant inh = inhabitants.byName(name);
                            if (inh == null) {
                                sender.sendMessage("§c❌ Habitant §f" + name + " §cintrouvable.");
                                return true;
                            }
                            String err = inhabitants.train(inh.id, args[args.length - 1].toLowerCase());
                            if (err != null) sender.sendMessage("§c❌ " + err);
                        }
                        case "renvoyer", "dismiss" -> {
                            if (args.length < 3) {
                                sender.sendMessage("§cUsage : /city garde renvoyer <nom>");
                                return true;
                            }
                            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                            com.citycore.inhabitant.InhabitantManager.Inhabitant inh = inhabitants.byName(name);
                            String err = inh == null ? "Garde " + name + " introuvable." : inhabitants.dismiss(inh.id);
                            if (err != null) sender.sendMessage("§c❌ " + err);
                        }
                        // /city garde test [nombre] [rôle] | test retirer : vrais gardes, sans lit ni formation
                        case "test" -> {
                            if (args.length >= 3 && (args[2].equalsIgnoreCase("retirer") || args[2].equalsIgnoreCase("clear"))) {
                                sender.sendMessage("§a✅ " + inhabitants.removeTestGuards() + " garde(s) de test retiré(s).");
                                return true;
                            }
                            if (inGameOnly(sender, player)) return true; // les gardes apparaissent près du joueur
                            int count = 1;
                            String roleKey = null;
                            for (int i = 2; i < args.length; i++) {
                                try { count = Integer.parseInt(args[i]); } catch (NumberFormatException e) { roleKey = args[i].toLowerCase(); }
                            }
                            if (roleKey != null && inhabitants.guard().role(roleKey) == null) {
                                player.sendMessage("§c❌ Rôle inconnu. Rôles : " + String.join(", ",
                                        inhabitants.guard().roles().stream().map(r -> r.key()).toList()));
                                return true;
                            }
                            count = Math.max(1, Math.min(20, count));
                            for (int i = 0; i < count; i++) inhabitants.spawnTestGuard(testSpot(player), roleKey);
                            player.sendMessage("§a✅ " + count + " garde(s) de test créé(s) §7(même comportement qu'un garde"
                                    + " formé chez Roland ; ni lit ni formation ; retirés au redémarrage ou par"
                                    + " §f/city garde test retirer§7).");
                        }
                        case "fin" -> sender.sendMessage("§a✅ " + inhabitants.finishTrainingNow()
                                + " recrue(s) devenue(s) garde(s).");
                        case "kill", "tuer" -> {
                            if (args.length < 3) {
                                sender.sendMessage("§cUsage : /city garde kill <nom>");
                                return true;
                            }
                            String target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                            if (!inhabitants.kill(target)) sender.sendMessage("§c❌ §f" + target + " §cintrouvable.");
                        }
                        default -> sender.sendMessage("§cUsage : /city " + sub.usageFor(sender));
                    }
                }

                // ── /city raid (plan « Raids ») ──────────────────
                // État pour tous ; essais, emplacements, catalogue : admin (citycore.admin.raid)
                case RAID -> {
                    if (raids != null) raids.handle(sender, player, args, admin);
                }

                // ── /city proto (phase 0, temporaire ; admin : citycore.admin.proto) ──
                case PROTO -> {
                    // Le soldat du prototype (phase 0) n'a pas le comportement de la garde : on crée un vrai
                    // garde de test à la place (rondes, front d'un siège, combat contre les soldats ennemis)
                    if (args.length >= 2 && args[1].equalsIgnoreCase("soldat") && inhabitants != null) {
                        inhabitants.spawnTestGuard(testSpot(player), args.length >= 3 ? args[2].toLowerCase() : null);
                        player.sendMessage("§a✅ Garde de test créé §7(comportement réel de la garde). Plusieurs à la fois :"
                                + " §f/city garde test <nombre> [rôle]§7 ; retirer : §f/city garde test retirer§7.");
                        return true;
                    }
                    if (prototype != null) prototype.handle(player, args);
                    if (args.length >= 2 && args[1].equalsIgnoreCase("clear") && inhabitants != null) {
                        int removed = inhabitants.removeTestGuards();
                        if (removed > 0) player.sendMessage("§a✅ " + removed + " garde(s) de test retiré(s).");
                    }
                }

                // ── /city npc (admin : citycore.admin.pnj) ───────
                case NPC -> {
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage : /city " + CitySubCommand.NPC.usage);
                        return true;
                    }

                    // Tests : tous les habitants d'un coup (menus complets sans atelier, voir TestActivation)
                    if (args[1].equalsIgnoreCase("tous") || args[1].equalsIgnoreCase("all")) {
                        String action = args[2].toLowerCase();
                        if (!action.equals("activer") && !action.equals("desactiver")) {
                            sender.sendMessage("§c❌ Avec §ftous§c : §factiver§c ou §fdesactiver§c.");
                            return true;
                        }
                        boolean on = action.equals("activer");
                        int changed = 0;
                        for (CityNPC n : CityNPC.values()) {
                            if (n == CityNPC.MAYOR) continue;
                            if (on ? com.citycore.npc.TestActivation.enable(n)
                                    : com.citycore.npc.TestActivation.disable(n)) changed++;
                        }
                        sender.sendMessage(on
                                ? "§a✅ Menus de test activés pour tous les habitants §7(" + changed + " de plus)§a. "
                                        + "§7Un clic droit d'administrateur ouvre leur menu complet, sans atelier ; "
                                        + "§f/city npc <type> menu §7l'ouvre à distance. Rien n'est enregistré."
                                : "§a✅ Menus de test désactivés §7(" + changed + " habitant(s))§a.");
                        return true;
                    }

                    CityNPC target = Arrays.stream(CityNPC.values())
                            .filter(n -> n != CityNPC.MAYOR)
                            .filter(n -> n.tag.replace("citycore_", "")
                                    .equalsIgnoreCase(args[1]))
                            .findFirst().orElse(null);

                    if (target == null) {
                        String available = Arrays.stream(CityNPC.values())
                                .filter(n -> n != CityNPC.MAYOR)
                                .map(n -> n.tag.replace("citycore_", ""))
                                .collect(java.util.stream.Collectors.joining("§7, §e"));
                        sender.sendMessage("§c❌ NPC inconnu. Disponibles : §e" + available);
                        return true;
                    }

                    switch (args[2].toLowerCase()) {
                        case "spawn" -> {
                            if (inGameOnly(sender, player)) return true; // apparaît devant l'administrateur
                            if (npcManager.getNPC(target) != null) {
                                player.sendMessage("§c❌ " + target.displayName
                                        + " §cexiste déjà.");
                                return true;
                            }
                            npcManager.spawnNPC(target, spawnLocation(player));
                            notificationManager.notifyAll(target, getServer(), npcManager);
                            player.sendMessage("§a✅ " + target.displayName
                                    + " §aest apparu !");
                        }
                        case "levelup" -> {
                            int lvl = npcDataManager.getLevel(target);
                            if (lvl >= 5) {
                                sender.sendMessage("§c❌ Niveau maximum atteint.");
                                return true;
                            }
                            npcDataManager.setLevel(target, lvl + 1, loadThresholds(target));
                            sender.sendMessage("§a✅ §e" + target.displayName
                                    + " §a→ niveau §e"
                                    + VillagerGUI.getLevelName(lvl + 1) + "§a.");
                        }
                        case "leveldown" -> {
                            int lvl = npcDataManager.getLevel(target);
                            if (lvl <= 1) {
                                sender.sendMessage("§c❌ Niveau minimum atteint.");
                                return true;
                            }
                            npcDataManager.setLevel(target, lvl - 1, loadThresholds(target));
                            sender.sendMessage("§a✅ §e" + target.displayName
                                    + " §a→ niveau §e"
                                    + VillagerGUI.getLevelName(lvl - 1) + "§a.");
                        }
                        case "remove" -> {
                            if (!npcManager.isSpawned(target)) {
                                sender.sendMessage("§c❌ §e" + target.displayName
                                        + " §cn'est pas spawné.");
                                return true;
                            }
                            npcManager.removeAndReset(target);
                            npcDataManager.resetNPC(target);
                            buildingManager.unassignNPCByTag(target.tag);
                            cityManager.removeResident();
                            sender.sendMessage("§a✅ §e" + target.displayName
                                    + " §asupprimé et remis à zéro !");
                        }
                        // Tests : menu complet sans atelier, pour les administrateurs, sans rien enregistrer
                        case "activer" -> {
                            com.citycore.npc.TestActivation.enable(target);
                            sender.sendMessage("§a✅ Menu de test activé pour " + target.displayName
                                    + "§a. §7Un clic droit d'administrateur ouvre son menu complet, sans atelier"
                                    + (npcManager.isSpawned(target) ? "" : " §8(pas encore apparu : §f/city npc "
                                            + args[1].toLowerCase() + " spawn§8 ou §fmenu§8)")
                                    + "§7. Rien n'est enregistré.");
                        }
                        case "desactiver" -> sender.sendMessage(com.citycore.npc.TestActivation.disable(target)
                                ? "§a✅ Menu de test désactivé pour " + target.displayName + "§a."
                                : "§7Le menu de test de " + target.displayName + " §7n'était pas activé.");
                        case "menu" -> {
                            if (inGameOnly(sender, player)) return true;
                            VillagerGUI menu = npcMenus.get(target);
                            if (menu == null) {
                                player.sendMessage("§c❌ Menu indisponible pour " + target.displayName + "§c.");
                                return true;
                            }
                            // Activé pour la session : les retours entre écrans restent sur le menu complet
                            if (com.citycore.npc.TestActivation.enable(target)) {
                                player.sendMessage("§7Menu de test activé pour " + target.displayName
                                        + " §8(/city npc " + args[1].toLowerCase() + " desactiver)§7.");
                            }
                            menu.open(player);
                        }
                        default -> sender.sendMessage(
                                "§c❌ Actions : §fspawn§c, §flevelUp§c, §flevelDown§c, §fremove§c, "
                                        + "§factiver§c, §fdesactiver§c, §fmenu");
                    }
                }

                // ── /city build ──────────────────────────────────
                // Admin (citycore.admin.batiments) : tout, sans passer par les PNJ. Joueurs : inspection,
                // bordures, étapes du bâton ; le reste chez Alderic ou l'artisan (voir playerBuildAction)
                case BUILD -> {
                    if (!admin && !playerBuildAction(player, args)) return true;
                    if (args.length == 1) { buildingGUI.open(player); return true; }

                    switch (args[1].toLowerCase()) {
                        // ── /city build new [type] [artisan|libre] [nom] ──
                        // Le type d'abord, le nom ensuite (demandé dans le chat s'il manque)
                        case "new" -> {
                            if (!cityManager.isCityInitialized()) {
                                player.sendMessage("§c❌ Aucune ville n'existe encore.");
                                return true;
                            }
                            // Sans type : menu de choix (ou boutons dans le chat sans le pack)
                            if (args.length < 3) {
                                buildingCreation.begin(player);
                                return true;
                            }
                            BuildingType type = BuildingType.parse(args[2]);
                            if (type == null) {
                                player.sendMessage("§6Choisissez d'abord le type du bâtiment §7(le nom vient ensuite) :");
                                buildingCreation.begin(player);
                                return true;
                            }
                            CityNPC owner = null;
                            int nameIndex = 3;
                            if (type.hasArtisan() && args.length > 3) { // atelier ou annexe : artisan
                                if (type == BuildingType.ATELIER && args[3].equalsIgnoreCase("libre")) {
                                    nameIndex = 4;
                                } else if (BuildingCreation.parseArtisan(args[3]) != null) {
                                    owner = BuildingCreation.parseArtisan(args[3]);
                                    nameIndex = 4;
                                }
                            }
                            if (args.length > nameIndex) {
                                String name = String.join("_", Arrays.copyOfRange(args, nameIndex, args.length));
                                buildingCreation.choose(player, name, type, owner);
                            } else {
                                buildingCreation.askName(player, type, owner);
                            }
                        }

                        // ── /city build inspect [nom] : bâtiment où l'on se trouve, ou par son nom ──
                        case "inspect", "inspecter" -> {
                            if (buildingInspector == null) return true;
                            Building target = args.length >= 3 ? buildingManager.getByName(args[2])
                                    : buildingManager.getBuildingAt(player.getWorld().getName(),
                                    player.getLocation().getBlockX(), player.getLocation().getBlockZ());
                            if (target == null) {
                                player.sendMessage(args.length >= 3
                                        ? "§c❌ Bâtiment §f" + args[2] + " §cintrouvable."
                                        : "§c❌ Vous n'êtes dans aucun bâtiment. §7/city build inspect <nom>");
                                return true;
                            }
                            com.citycore.building.Inspection insp = buildingInspector.recheck(target.id(), player);
                            if (insp != null) com.citycore.building.BuildingInspector.report(player, insp);
                        }

                        // ── /city build type <nom> <type> [artisan] ──
                        case "type" -> {
                            if (args.length < 4) {
                                player.sendMessage("§cUsage : /city build type <nom> <habitation|atelier|mairie|divers> [artisan]");
                                return true;
                            }
                            Building target = buildingManager.getByName(args[2]);
                            if (target == null) {
                                player.sendMessage("§c❌ Bâtiment §f" + args[2] + " §cintrouvable.");
                                return true;
                            }
                            BuildingType type = BuildingType.parse(args[3]);
                            if (type == null) {
                                player.sendMessage("§c❌ Type inconnu : §f" + args[3] + "§c.");
                                return true;
                            }
                            CityNPC owner = null;
                            if (args.length >= 5) {
                                owner = BuildingCreation.parseArtisan(args[4]);
                                if (owner == null || type != BuildingType.ATELIER) {
                                    player.sendMessage(type != BuildingType.ATELIER
                                            ? "§c❌ Seul un atelier peut être réservé à un artisan."
                                            : "§c❌ Artisan inconnu : §f" + args[4] + "§c.");
                                    return true;
                                }
                            }
                            buildingCreation.retype(player, target, type, owner);
                        }

                        // ── /city build hauteur <nom> <bas> <haut> ──
                        // ── /city build hauteur <nom> auto | <sol> | <bas> <haut> ──
                        // Zone de recherche : par défaut profondeur sous le sol de définition et
                        // hauteur au-dessus (config.yml) ; la hauteur construite se mesure seule.
                        case "hauteur", "height" -> {
                            if (args.length < 4) {
                                player.sendMessage("§cUsage : /city build hauteur <nom> auto §7(règle de config.yml)");
                                player.sendMessage("§c       /city build hauteur <nom> <sol> §7(nouveau sol de définition)");
                                player.sendMessage("§c       /city build hauteur <nom> <bas> <haut> §7(zone manuelle)");
                                return true;
                            }
                            Building target = buildingManager.getByName(args[2]);
                            if (target == null) {
                                player.sendMessage("§c❌ Bâtiment §f" + args[2] + " §cintrouvable.");
                                return true;
                            }
                            org.bukkit.World w = Bukkit.getWorld(target.world());
                            if (w == null) return true;
                            int low, high;
                            try {
                                if (args[3].equalsIgnoreCase("auto")) {
                                    int ref = target.refY() != null ? target.refY()
                                            : BuildingHeights.groundLevel(w, target.x1(), target.z1(), target.x2(), target.z2());
                                    int[] h = BuildingHeights.range(w, ref, ref);
                                    buildingManager.setReference(target.id(), ref);
                                    low = h[0];
                                    high = h[1];
                                } else if (args.length == 4) {
                                    int ref = Integer.parseInt(args[3]);
                                    int[] h = BuildingHeights.range(w, ref, ref);
                                    buildingManager.setReference(target.id(), ref);
                                    low = h[0];
                                    high = h[1];
                                } else {
                                    low  = Integer.parseInt(args[3]);
                                    high = Integer.parseInt(args[4]);
                                }
                            } catch (NumberFormatException e) {
                                player.sendMessage("§c❌ Hauteurs invalides (nombres entiers attendus).");
                                return true;
                            }
                            if (Math.abs(high - low) < 2) {
                                player.sendMessage("§c❌ La zone doit faire au moins 2 blocs de haut.");
                                return true;
                            }
                            buildingManager.setHeight(target.id(), low, high);
                            if (buildingInspector != null) {
                                buildingInspector.refreshBuildings();
                                buildingInspector.recheck(target.id(), null);
                            }
                            player.sendMessage("§a✅ §e" + target.name() + " §a: zone de recherche Y §f"
                                    + Math.min(low, high) + " §a→ §f" + Math.max(low, high) + "§a.");
                        }
                        case "show"   -> buildingBorderTask.toggle(player);
                        case "remove" -> {
                            if (args.length < 3) {
                                player.sendMessage("§cUsage : /city build remove <nom|all>");
                                return true;
                            }
                            if (args[2].equalsIgnoreCase("all")) { // admin seulement (voir playerBuildAction)
                                buildingManager.removeAll();
                                player.sendMessage("§a✅ Tous les bâtiments supprimés.");
                                return true;
                            }
                            if (!buildingManager.nameExists(args[2])) {
                                player.sendMessage("§c❌ Bâtiment §f" + args[2]
                                        + " §cintrouvable.");
                                return true;
                            }
                            buildingManager.removeByName(args[2]);
                            player.sendMessage("§a✅ Bâtiment §e" + args[2]
                                    + " §asupprimé.");
                        }
                        case "assign" -> {
                            if (args.length < 4) {
                                player.sendMessage("§cUsage : /city build assign <bâtiment> <npc>");
                                return true;
                            }
                            Building bTarget = buildingManager.getAllBuildings().stream()
                                    .filter(b -> b.name().equalsIgnoreCase(args[2]))
                                    .findFirst().orElse(null);
                            if (bTarget == null) {
                                player.sendMessage("§c❌ Bâtiment §f" + args[2]
                                        + " §cintrouvable.");
                                return true;
                            }
                            if (buildingManager.buildingHasNPC(bTarget.id())) {
                                player.sendMessage("§c❌ Bâtiment déjà assigné.");
                                return true;
                            }
                            CityNPC npc = BuildingCreation.parseArtisan(args[3]);
                            if (npc == null) {
                                player.sendMessage("§c❌ NPC §f" + args[3]
                                        + " §cintrouvable.");
                                return true;
                            }
                            if (buildingManager.isNPCAlreadyAssigned(npc.tag)) {
                                player.sendMessage("§c❌ §e" + npc.displayName
                                        + " §cdéjà assigné ailleurs.");
                                return true;
                            }
                            // Même règle que « Choisir son logis » : un atelier libre ou réservé à lui
                            if (!bTarget.acceptsArtisan(npc)) {
                                player.sendMessage(bTarget.type() != BuildingType.ATELIER
                                        ? "§c❌ §e" + bTarget.name() + " §cn'est pas un atelier ("
                                          + bTarget.type().label + "). §7/city build type " + bTarget.name() + " atelier"
                                        : "§c❌ §e" + bTarget.name() + " §cest réservé à "
                                          + CityNPC.fromTag(bTarget.reservedFor()).displayName + "§c.");
                                return true;
                            }
                            Building elsewhere = buildingManager.findReservedFor(npc.tag, bTarget.id());
                            if (elsewhere != null) {
                                player.sendMessage("§c❌ §e" + npc.displayName + " §ca déjà un atelier réservé : §e"
                                        + elsewhere.name() + "§c.");
                                return true;
                            }
                            // Inspection : l'atelier doit contenir ce que demande ce personnage (phase 2)
                            if (buildingInspector != null) {
                                buildingInspector.invalidate(bTarget.id());
                                com.citycore.building.Inspection insp = buildingInspector.inspectFor(bTarget, npc);
                                if (!insp.compliant()) {
                                    player.sendMessage("§c❌ §e" + bTarget.name() + " §cest incomplet pour "
                                            + npc.displayName + "§c : il manque §f" + insp.missing() + "§c.");
                                    return true;
                                }
                            }
                            buildingManager.assignNPC(bTarget.id(), npc.tag);
                            notificationManager.notifyAll(npc, getServer(), npcManager);
                            npcDataManager.setState(npc, NPCState.ASSIGNED);
                            if (buildingInspector != null) buildingInspector.onAssigned(npc, bTarget);
                            List<String> lines = npc.getDialogue("building_assign");
                            if (!lines.isEmpty()) {
                                net.citizensnpcs.api.npc.NPC citizensNPC =
                                        npcManager.getNPC(npc);
                                if (citizensNPC != null && citizensNPC.isSpawned()) {
                                    citizensNPC.getEntity().getLocation().getWorld()
                                            .getPlayers().stream()
                                            .filter(p -> p.getLocation().distance(
                                                    citizensNPC.getEntity().getLocation()) <= 16)
                                            .forEach(p -> TypewriterUtil.play(
                                                    plugin, p, lines, null));
                                }
                            }
                            player.sendMessage("§a✅ §e" + npc.displayName
                                    + " §aassigné à §e" + bTarget.name() + "§a !");
                        }
                        case "skip" -> {
                            if (!buildingSession.isNpcPointPhase(player.getUniqueId())) {
                                player.sendMessage("§c❌ Aucune session en cours.");
                                return true;
                            }
                            BuildingSession.PendingBuilding pending =
                                    buildingSession.getPendingBuilding(player.getUniqueId());
                            buildingCreation.finish(player, pending, null, null, null, null);
                            player.sendMessage("§7(sans point NPC)");
                            buildingSession.clear(player.getUniqueId());
                            player.getInventory().setItemInMainHand(null);
                        }
                        default -> player.sendMessage(
                                "§cUsage : /city build [new|inspect|show|remove|assign|skip|type|hauteur]");
                    }
                }

                // ── /city job (admin : citycore.admin.joueurs ; joueurs : métier chez l'artisan) ──
                case JOB -> {
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage : /city job <joueur> <npc|clear>");
                        return true;
                    }
                    Player jTarget = Bukkit.getPlayer(args[1]);
                    if (jTarget == null) {
                        sender.sendMessage("§c❌ Joueur introuvable.");
                        return true;
                    }
                    if (args[2].equalsIgnoreCase("clear")) {
                        playerDataManager.clearJob(jTarget.getUniqueId());
                        sender.sendMessage("§a✅ Job de §e" + jTarget.getName()
                                + " §aeffacé.");
                        jTarget.sendMessage("§7Votre job a été réinitialisé.");
                        return true;
                    }
                    CityNPC jNpc = Arrays.stream(CityNPC.values())
                            .filter(n -> n.tag.replace("citycore_", "")
                                    .equalsIgnoreCase(args[2]))
                            .findFirst().orElse(null);
                    if (jNpc == null) {
                        sender.sendMessage("§c❌ NPC §f" + args[2] + " §cintrouvable.");
                        return true;
                    }
                    playerDataManager.setJob(jTarget.getUniqueId(), jNpc);
                    sender.sendMessage("§a✅ Job de §e" + jTarget.getName()
                            + " §a: §e" + jNpc.displayName);
                    jTarget.sendMessage("§a✅ Votre job : §e" + jNpc.displayName);
                }

                // ── /city player (admin : citycore.admin.joueurs) ──
                case PLAYER -> {
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage : /city player <joueur>"
                                + " <levelUp|levelDown|setLevel> [nombre]");
                        return true;
                    }
                    Player pTarget = Bukkit.getPlayer(args[1]);
                    if (pTarget == null) {
                        sender.sendMessage("§c❌ Joueur §f" + args[1]
                                + " §cintrouvable.");
                        return true;
                    }
                    int currentLevel = playerDataManager.getLevel(pTarget.getUniqueId());
                    switch (args[2].toLowerCase()) {
                        case "levelup" -> {
                            playerDataManager.setLevelAndXP(
                                    pTarget.getUniqueId(), currentLevel + 1, 0);
                            sender.sendMessage("§a✅ §e" + pTarget.getName()
                                    + " §a→ niveau §e" + (currentLevel + 1) + "§a.");
                            pTarget.sendMessage("§a🎉 Vous êtes passé niveau §e"
                                    + (currentLevel + 1) + "§a !");
                            pTarget.sendTitle("§6⬆ Niveau " + (currentLevel + 1),
                                    "§7Promu par un administrateur", 10, 40, 10);
                        }
                        case "leveldown" -> {
                            if (currentLevel <= 1) {
                                sender.sendMessage("§c❌ §e" + pTarget.getName()
                                        + " §cest déjà niveau 1.");
                                return true;
                            }
                            playerDataManager.setLevelAndXP(
                                    pTarget.getUniqueId(), currentLevel - 1, 0);
                            sender.sendMessage("§a✅ §e" + pTarget.getName()
                                    + " §a→ niveau §e" + (currentLevel - 1) + "§a.");
                            pTarget.sendMessage("§7Rétrogradé niveau §e"
                                    + (currentLevel - 1) + "§7.");
                        }
                        case "setlevel" -> {
                            if (args.length < 4) {
                                sender.sendMessage("§cUsage : /city player <joueur>"
                                        + " setLevel <nombre>");
                                return true;
                            }
                            int targetLevel;
                            try {
                                targetLevel = Integer.parseInt(args[3]);
                            } catch (NumberFormatException e) {
                                sender.sendMessage("§c❌ Nombre invalide.");
                                return true;
                            }
                            if (targetLevel < 1) {
                                sender.sendMessage("§c❌ Niveau minimum : 1.");
                                return true;
                            }
                            playerDataManager.setLevelAndXP(
                                    pTarget.getUniqueId(), targetLevel, 0);
                            sender.sendMessage("§a✅ Niveau de §e" + pTarget.getName()
                                    + " §adéfini à §e" + targetLevel + "§a.");
                            pTarget.sendMessage("§7Niveau défini à §e" + targetLevel
                                    + " §7par un administrateur.");
                            pTarget.sendTitle("§6Niveau " + targetLevel,
                                    "§7Défini par un administrateur", 10, 40, 10);
                        }
                        default -> sender.sendMessage("§cActions : levelUp, levelDown, setLevel");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage("§c❌ Erreur interne.");
        }

        return true;
    }

    /**
     * Aide : seulement ce que l'expéditeur a le droit d'utiliser (la partie « joueur » d'une
     * sous-commande mixte pour un joueur ; depuis la console, ce qui s'y utilise).
     */
    private void sendHelp(CommandSender sender) {
        boolean console = !(sender instanceof Player);
        sender.sendMessage("§8§m--------------------");
        sender.sendMessage("§6 CityCore §7— Commandes");
        sender.sendMessage("§8§m--------------------");
        for (CitySubCommand cmd : CitySubCommand.values()) {
            if (!CityPermissions.canUse(sender, cmd) || (console && !cmd.rights.console())) continue;
            sender.sendMessage("§e/city " + cmd.usageFor(sender) + " §7— " + cmd.descriptionFor(sender));
        }
        if (!sender.hasPermission(CityPermissions.ADMIN)) {
            sender.sendMessage("§7Le reste se fait auprès des habitants : §6Alderic §7(maire : bâtiments,"
                    + " parcelles, trésor), les artisans (logis, emplois), §9Roland §7(garde).");
        }
        sender.sendMessage("§8§m--------------------");
    }

    private Location spawnLocation(Player player) {
        Location loc = player.getLocation().clone();
        loc.add(loc.getDirection().normalize().multiply(2));
        loc.setY(Math.floor(loc.getY() + 1));
        loc.setYaw((player.getLocation().getYaw() + 180) % 360);
        loc.setPitch(0);
        return loc;
    }

    private Map<Integer, Integer> loadThresholds(CityNPC target) {
        Map<Integer, Integer> thresholds = new java.util.HashMap<>();
        String key = target.tag.replace("citycore_", "");
        var section = plugin.getConfig().getConfigurationSection(
                key + ".level-thresholds");
        if (section != null) {
            for (String k : section.getKeys(false)) {
                thresholds.put(Integer.parseInt(k), section.getInt(k));
            }
        }
        return thresholds;
    }
}