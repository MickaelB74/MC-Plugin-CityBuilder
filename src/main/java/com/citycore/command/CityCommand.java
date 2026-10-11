package com.citycore.command;

import com.citycore.building.*;
import com.citycore.city.City;
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

    /** Prototype de la phase 0 (/city proto) : PNJ blessables, foule, combat. Temporaire. */
    private com.citycore.debug.NpcPrototype prototype;

    public void setPrototype(com.citycore.debug.NpcPrototype prototype) {
        this.prototype = prototype;
    }

    /** Menu illustré « Quêtes suivies » (/city quests). */
    public void setQuestTracking(com.citycore.ui.screens.QuestTrackingScreen questTracking) {
        this.questTracking = questTracking;
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

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande joueur uniquement.");
            return true;
        }

        if (args.length == 0) { sendHelp(player); return true; }

        CitySubCommand sub = CitySubCommand.fromLabel(args[0]);
        if (sub == null) { sendHelp(player); return true; }

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

                // ── /city claim ──────────────────────────────────
                case CLAIM -> {
                    if (!cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    Chunk chunk = player.getLocation().getChunk();
                    if (cityManager.isChunkClaimed(chunk)) {
                        player.sendMessage("§c❌ Ce chunk est déjà claimé.");
                        return true;
                    }
                    if (!cityManager.isAdjacentToClaimed(chunk)) {
                        player.sendMessage("§c❌ Ce chunk doit être adjacent à un chunk déjà claimé.");
                        return true;
                    }
                    if (!cityManager.canClaimChunk()) {
                        int price = cityManager.getNextExpandPrice();
                        player.sendMessage("§c❌ Limite atteinte §7(§f"
                                + cityManager.getClaimedChunkCount() + "§7/§f"
                                + cityManager.getMaxChunks() + "§7).");
                        player.sendMessage("§7💡 §e/city expand §7pour +1 slot · prix : §6"
                                + price + " coins");
                        return true;
                    }
                    cityManager.claimChunk(chunk);
                    player.sendMessage("§a✅ Chunk claimé ! §7(§f"
                            + cityManager.getClaimedChunkCount() + "§7/§f"
                            + cityManager.getMaxChunks() + "§7)");
                }

                // ── /city unclaim ────────────────────────────────
                case UNCLAIM -> {
                    if (!cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    Chunk chunk = player.getLocation().getChunk();
                    if (!cityManager.isChunkClaimed(chunk)) {
                        player.sendMessage("§c❌ Ce chunk n'appartient pas à la ville.");
                        return true;
                    }
                    if (cityManager.getClaimedChunkCount() <= 1) {
                        player.sendMessage("§c❌ Impossible de retirer le dernier chunk.");
                        return true;
                    }
                    cityManager.unclaimChunk(chunk);
                    player.sendMessage("§e🗑 Chunk retiré. §7(§f"
                            + cityManager.getClaimedChunkCount() + "§7/§f"
                            + cityManager.getMaxChunks() + "§7)");
                }

                // ── /city expand ─────────────────────────────────
                case EXPAND -> {
                    if (!player.isOp()) {
                        player.sendMessage("§c❌ Parlez au §6Maire §cpour agrandir la ville.");
                        return true;
                    }
                    if (!cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    int price   = cityManager.getNextExpandPrice();
                    int balance = cityManager.getCityCoins();
                    player.sendMessage("§7Prix : §6" + price
                            + " coins §7· Caisse : §6" + balance + " coins");
                    CityManager.ExpandResult result = cityManager.expandMaxChunks();
                    if (result.success()) {
                        player.sendMessage("§a✅ Capacité étendue ! §7Max : §f"
                                + result.newMaxChunks());
                        player.sendMessage("§7Caisse restante : §6"
                                + result.newBalance() + " coins");
                    } else {
                        player.sendMessage("§c❌ Fonds insuffisants. Il manque §f"
                                + (price - balance) + " coins.");
                    }
                }

                // ── /city deposit ────────────────────────────────
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
                        player.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    player.sendMessage("§8§m--------------------");
                    player.sendMessage("§6 " + city.getName());
                    player.sendMessage("§8§m--------------------");
                    player.sendMessage("§eNiveau  : §f" + city.getLevel());
                    player.sendMessage("§eCaisse  : §6" + city.getCoins() + " coins");
                    player.sendMessage("§eChunks  : §f" + city.getClaimedChunks()
                            + " §7/ §f" + city.getMaxChunks());
                    player.sendMessage("§eExpand  : §6" + cityManager.getNextExpandPrice()
                            + " coins §7pour +1 slot");
                    player.sendMessage("§8§m--------------------");
                }

                // ── /city quests ─────────────────────────────────
                case QUESTS -> {
                    // /city quests toggle : affiche ou masque le suivi ; /city quests recits : onglet
                    // des récits ; sinon onglet du suivi (menu illustré, ou classique sans le pack)
                    String option = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
                    switch (option) {
                        case "toggle" -> questHUD.toggle(player);
                        case "recits", "récits" -> {
                            if (questTracking == null || !questTracking.openStories(player)) questSelectionGUI.openStories(player);
                        }
                        default -> {
                            if (questTracking == null || !questTracking.open(player)) questSelectionGUI.open(player);
                        }
                    }
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
                    if (!player.isOp()) {
                        player.sendMessage("§c❌ Réservé aux admins.");
                        return true;
                    }
                    if (economyCheck == null) return true;
                    List<String> warnings = economyCheck.get();
                    if (warnings.isEmpty()) {
                        player.sendMessage("§a✅ Économie saine : aucun arbitrage achat → craft → revente.");
                    } else {
                        player.sendMessage("§6⚠ " + warnings.size() + " alerte(s) économiques :");
                        warnings.stream().limit(15).forEach(w -> player.sendMessage("§7- §e" + w));
                        if (warnings.size() > 15) player.sendMessage("§8… voir la console pour la suite.");
                        warnings.forEach(w -> plugin.getLogger().warning("⚠ [Économie] " + w));
                    }
                }

                // ── /city habitants (phase 3) ────────────────────
                case HABITANTS -> {
                    if (inhabitants == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "list";
                    if (!action.equals("list") && !player.isOp()) {
                        player.sendMessage("§c❌ Réservé aux admins (actions de test).");
                        return true;
                    }
                    switch (action) {
                        case "list", "liste" -> inhabitants.list(player);
                        case "arrivee", "arrivée" -> player.sendMessage(inhabitants.arrive()
                                ? "§a✅ Un habitant arrive." : "§7Aucun lit libre dans une habitation conforme.");
                        case "kill", "tuer" -> {
                            if (args.length < 3) {
                                player.sendMessage("§cUsage : /city habitants kill <nom>");
                                return true;
                            }
                            String target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                            if (!inhabitants.kill(target)) player.sendMessage("§c❌ Habitant §f" + target + " §cintrouvable.");
                        }
                        case "impot", "impôt" -> {
                            int paid = inhabitants.payTax();
                            if (paid == 0) player.sendMessage("§7Aucun habitant : aucun impôt versé.");
                        }
                        case "deuil" -> player.sendMessage("§a✅ " + inhabitants.endAllMourning()
                                + " lit(s) libéré(s) : de nouveaux habitants pourront arriver.");
                        case "recharger", "reload" -> {
                            inhabitants.loadSettings();
                            player.sendMessage("§a✅ Réglages des habitants relus (section habitants de config.yml).");
                        }
                        default -> player.sendMessage("§cUsage : /city habitants [list|arrivee|kill <nom>|impot|deuil|recharger]");
                    }
                }

                // ── /city siege (phase 4) ────────────────────────
                case SIEGE -> {
                    if (siege == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "etat";
                    if (!action.equals("etat") && !action.equals("état") && !player.isOp()) {
                        player.sendMessage("§c❌ Réservé aux admins (actions de test).");
                        return true;
                    }
                    switch (action) {
                        case "etat", "état", "status" -> siege.status().forEach(player::sendMessage);
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
                                    player.sendMessage("§cUsage : /city siege start [palier 1-10] [monstres|banniere]");
                                    return true;
                                }
                            }
                            String err = siege.begin(tier, true, kind);
                            if (err != null) player.sendMessage("§c❌ " + err);
                        }
                        case "stop", "arreter", "arrêter" -> {
                            if (!siege.stop()) player.sendMessage("§7Aucun siège en cours.");
                        }
                        case "next", "suivante" -> {
                            if (!siege.skipWave()) player.sendMessage("§7Aucun siège en cours.");
                        }
                        default -> player.sendMessage("§cUsage : /city siege [start [palier]|stop|next]");
                    }
                }

                // ── /city event (phase 4) ────────────────────────
                case EVENT -> {
                    if (calendar == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "list";
                    if (!action.equals("list") && !action.equals("liste") && !player.isOp()) {
                        player.sendMessage("§c❌ Réservé aux admins (actions de test).");
                        return true;
                    }
                    switch (action) {
                        case "list", "liste" -> calendar.listing().forEach(player::sendMessage);
                        case "start", "lancer" -> {
                            com.citycore.siege.EventCalendar.Event event = calendar.event(args.length >= 3 ? args[2] : "siege");
                            if (event == null) {
                                player.sendMessage("§c❌ Type inconnu. Types : " + String.join(", ", calendar.types()));
                                return true;
                            }
                            String err = event.start();
                            if (err != null) player.sendMessage("§c❌ " + err);
                        }
                        case "stop", "arreter", "arrêter" -> {
                            boolean stopped = false;
                            for (String type : calendar.types()) stopped |= calendar.event(type).stop();
                            if (!stopped) player.sendMessage("§7Aucun événement en cours.");
                        }
                        case "recharger", "reload" -> {
                            calendar.reload();
                            siege.reload();
                            player.sendMessage("§a✅ Calendrier et réglages des sièges relus (sections evenements et sieges).");
                        }
                        default -> player.sendMessage("§cUsage : /city event [list|start <type>|stop|recharger]");
                    }
                }

                // ── /city garde (phase 5) ────────────────────────
                // Même chose que le menu de Roland (commun à tous) ; fin et kill sont des actions de test
                case GARDE -> {
                    if (inhabitants == null) return true;
                    String action = args.length >= 2 ? args[1].toLowerCase() : "list";
                    if ((action.equals("fin") || action.equals("kill") || action.equals("tuer") || action.equals("test"))
                            && !player.isOp()) {
                        player.sendMessage("§c❌ Réservé aux admins (actions de test).");
                        return true;
                    }
                    switch (action) {
                        case "list", "liste" -> inhabitants.listGuard(player);
                        case "former", "train" -> {
                            if (args.length < 4) {
                                player.sendMessage("§cUsage : /city garde former <habitant> <rôle>");
                                return true;
                            }
                            // Le rôle est le dernier mot, le nom peut en contenir plusieurs (« Jehan 2 »)
                            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1));
                            com.citycore.inhabitant.InhabitantManager.Inhabitant inh = inhabitants.byName(name);
                            if (inh == null) {
                                player.sendMessage("§c❌ Habitant §f" + name + " §cintrouvable.");
                                return true;
                            }
                            String err = inhabitants.train(inh.id, args[args.length - 1].toLowerCase());
                            if (err != null) player.sendMessage("§c❌ " + err);
                        }
                        case "renvoyer", "dismiss" -> {
                            if (args.length < 3) {
                                player.sendMessage("§cUsage : /city garde renvoyer <nom>");
                                return true;
                            }
                            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                            com.citycore.inhabitant.InhabitantManager.Inhabitant inh = inhabitants.byName(name);
                            String err = inh == null ? "Garde " + name + " introuvable." : inhabitants.dismiss(inh.id);
                            if (err != null) player.sendMessage("§c❌ " + err);
                        }
                        // /city garde test [nombre] [rôle] | test retirer : vrais gardes, sans lit ni formation
                        case "test" -> {
                            if (args.length >= 3 && (args[2].equalsIgnoreCase("retirer") || args[2].equalsIgnoreCase("clear"))) {
                                player.sendMessage("§a✅ " + inhabitants.removeTestGuards() + " garde(s) de test retiré(s).");
                                return true;
                            }
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
                        case "fin" -> player.sendMessage("§a✅ " + inhabitants.finishTrainingNow()
                                + " recrue(s) devenue(s) garde(s).");
                        case "kill", "tuer" -> {
                            if (args.length < 3) {
                                player.sendMessage("§cUsage : /city garde kill <nom>");
                                return true;
                            }
                            String target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                            if (!inhabitants.kill(target)) player.sendMessage("§c❌ §f" + target + " §cintrouvable.");
                        }
                        default -> player.sendMessage("§cUsage : /city garde [list|former <habitant> <rôle>|renvoyer <nom>|fin|kill <nom>]");
                    }
                }

                // ── /city proto (phase 0, temporaire) ────────────
                case PROTO -> {
                    if (!player.isOp()) {
                        player.sendMessage("§c❌ Réservé aux admins.");
                        return true;
                    }
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

                // ── /city npc ────────────────────────────────────
                case NPC -> {
                    if (args.length < 3) {
                        player.sendMessage("§cUsage : /city npc <type> <action>");
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
                        player.sendMessage("§c❌ NPC inconnu. Disponibles : §e" + available);
                        return true;
                    }

                    switch (args[2].toLowerCase()) {
                        case "spawn" -> {
                            if (!player.isOp()) {
                                player.sendMessage("§c❌ Réservé aux admins.");
                                return true;
                            }
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
                            if (!player.isOp()) return false;
                            int lvl = npcDataManager.getLevel(target);
                            if (lvl >= 5) {
                                player.sendMessage("§c❌ Niveau maximum atteint.");
                                return true;
                            }
                            npcDataManager.setLevel(target, lvl + 1, loadThresholds(target));
                            player.sendMessage("§a✅ §e" + target.displayName
                                    + " §a→ niveau §e"
                                    + VillagerGUI.getLevelName(lvl + 1) + "§a.");
                        }
                        case "leveldown" -> {
                            if (!player.isOp()) return false;
                            int lvl = npcDataManager.getLevel(target);
                            if (lvl <= 1) {
                                player.sendMessage("§c❌ Niveau minimum atteint.");
                                return true;
                            }
                            npcDataManager.setLevel(target, lvl - 1, loadThresholds(target));
                            player.sendMessage("§a✅ §e" + target.displayName
                                    + " §a→ niveau §e"
                                    + VillagerGUI.getLevelName(lvl - 1) + "§a.");
                        }
                        case "remove" -> {
                            if (!player.isOp()) {
                                player.sendMessage("§c❌ Réservé aux Op.");
                                return true;
                            }
                            if (!npcManager.isSpawned(target)) {
                                player.sendMessage("§c❌ §e" + target.displayName
                                        + " §cn'est pas spawné.");
                                return true;
                            }
                            npcManager.removeAndReset(target);
                            npcDataManager.resetNPC(target);
                            buildingManager.unassignNPCByTag(target.tag);
                            cityManager.removeResident();
                            player.sendMessage("§a✅ §e" + target.displayName
                                    + " §asupprimé et remis à zéro !");
                        }
                        default -> player.sendMessage(
                                "§c❌ Actions : §fspawn§c, §flevelUp§c, §flevelDown§c, §fremove");
                    }
                }

                // ── /city build ──────────────────────────────────
                case BUILD -> {
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
                            if (type == BuildingType.ATELIER && args.length > 3) {
                                if (args[3].equalsIgnoreCase("libre")) {
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
                            if (args[2].equalsIgnoreCase("all")) {
                                if (!player.isOp()) {
                                    player.sendMessage("§c❌ Réservé aux admins.");
                                    return true;
                                }
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
                                "§cUsage : /city build [new|show|remove|assign|skip|type|hauteur]");
                    }
                }

                // ── /city job ────────────────────────────────────
                case JOB -> {
                    if (!player.isOp()) return true;
                    if (args.length < 3) {
                        player.sendMessage("§cUsage : /city job <joueur> <npc|clear>");
                        return true;
                    }
                    Player jTarget = Bukkit.getPlayer(args[1]);
                    if (jTarget == null) {
                        player.sendMessage("§c❌ Joueur introuvable.");
                        return true;
                    }
                    if (args[2].equalsIgnoreCase("clear")) {
                        playerDataManager.clearJob(jTarget.getUniqueId());
                        player.sendMessage("§a✅ Job de §e" + jTarget.getName()
                                + " §aeffacé.");
                        jTarget.sendMessage("§7Votre job a été réinitialisé.");
                        return true;
                    }
                    CityNPC jNpc = Arrays.stream(CityNPC.values())
                            .filter(n -> n.tag.replace("citycore_", "")
                                    .equalsIgnoreCase(args[2]))
                            .findFirst().orElse(null);
                    if (jNpc == null) {
                        player.sendMessage("§c❌ NPC §f" + args[2] + " §cintrouvable.");
                        return true;
                    }
                    playerDataManager.setJob(jTarget.getUniqueId(), jNpc);
                    player.sendMessage("§a✅ Job de §e" + jTarget.getName()
                            + " §a: §e" + jNpc.displayName);
                    jTarget.sendMessage("§a✅ Votre job : §e" + jNpc.displayName);
                }

                // ── /city player ─────────────────────────────────
                case PLAYER -> {
                    if (!player.isOp()) return true;
                    if (args.length < 3) {
                        player.sendMessage("§cUsage : /city player <joueur>"
                                + " <levelUp|levelDown|setLevel> [nombre]");
                        return true;
                    }
                    Player pTarget = Bukkit.getPlayer(args[1]);
                    if (pTarget == null) {
                        player.sendMessage("§c❌ Joueur §f" + args[1]
                                + " §cintrouvable.");
                        return true;
                    }
                    int currentLevel = playerDataManager.getLevel(pTarget.getUniqueId());
                    switch (args[2].toLowerCase()) {
                        case "levelup" -> {
                            playerDataManager.setLevelAndXP(
                                    pTarget.getUniqueId(), currentLevel + 1, 0);
                            player.sendMessage("§a✅ §e" + pTarget.getName()
                                    + " §a→ niveau §e" + (currentLevel + 1) + "§a.");
                            pTarget.sendMessage("§a🎉 Vous êtes passé niveau §e"
                                    + (currentLevel + 1) + "§a !");
                            pTarget.sendTitle("§6⬆ Niveau " + (currentLevel + 1),
                                    "§7Promu par un administrateur", 10, 40, 10);
                        }
                        case "leveldown" -> {
                            if (currentLevel <= 1) {
                                player.sendMessage("§c❌ §e" + pTarget.getName()
                                        + " §cest déjà niveau 1.");
                                return true;
                            }
                            playerDataManager.setLevelAndXP(
                                    pTarget.getUniqueId(), currentLevel - 1, 0);
                            player.sendMessage("§a✅ §e" + pTarget.getName()
                                    + " §a→ niveau §e" + (currentLevel - 1) + "§a.");
                            pTarget.sendMessage("§7Rétrogradé niveau §e"
                                    + (currentLevel - 1) + "§7.");
                        }
                        case "setlevel" -> {
                            if (args.length < 4) {
                                player.sendMessage("§cUsage : /city player <joueur>"
                                        + " setLevel <nombre>");
                                return true;
                            }
                            int targetLevel;
                            try {
                                targetLevel = Integer.parseInt(args[3]);
                            } catch (NumberFormatException e) {
                                player.sendMessage("§c❌ Nombre invalide.");
                                return true;
                            }
                            if (targetLevel < 1) {
                                player.sendMessage("§c❌ Niveau minimum : 1.");
                                return true;
                            }
                            playerDataManager.setLevelAndXP(
                                    pTarget.getUniqueId(), targetLevel, 0);
                            player.sendMessage("§a✅ Niveau de §e" + pTarget.getName()
                                    + " §adéfini à §e" + targetLevel + "§a.");
                            pTarget.sendMessage("§7Niveau défini à §e" + targetLevel
                                    + " §7par un administrateur.");
                            pTarget.sendTitle("§6Niveau " + targetLevel,
                                    "§7Défini par un administrateur", 10, 40, 10);
                        }
                        default -> player.sendMessage("§cActions : levelUp, levelDown, setLevel");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage("§c❌ Erreur interne.");
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8§m--------------------");
        player.sendMessage("§6 CityCore §7— Commandes");
        player.sendMessage("§8§m--------------------");
        for (CitySubCommand cmd : CitySubCommand.values()) {
            player.sendMessage("§e/city " + cmd.usage + " §7— " + cmd.description);
        }
        player.sendMessage("§8§m--------------------");
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