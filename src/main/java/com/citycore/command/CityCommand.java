package com.citycore.command;

import com.citycore.CityCoreHUD;
import com.citycore.building.*;
import com.citycore.city.City;
import com.citycore.npc.*;
import com.citycore.npc.villager.VillagerGUI;
import com.citycore.player.PlayerDataManager;
import com.citycore.quest.QuestHUD;
import com.citycore.util.ChunkParticleTask;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    public CityCommand(CityManager cityManager, NPCManager npcManager,
                       JavaPlugin plugin, NPCDataManager npcDataManager,
                       QuestHUD questHUD, BuildingManager buildingManager,
                       BuildingSession buildingSession, BuildingGUI buildingGUI,
                       BuildingBorderTask buildingBorderTask,
                       NPCNotificationManager notificationManager,
                       PlayerDataManager playerDataManager) {
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
                    if (args.length < 2) {
                        player.sendMessage("§cUsage : /city " + CitySubCommand.CREATE.usage);
                        return true;
                    }
                    String cityName = String.join(" ",
                            Arrays.copyOfRange(args, 1, args.length));
                    cityManager.initializeCity(cityName);
                    cityManager.claimChunk(player.getLocation().getChunk());
                    npcManager.spawnMayor(player);
                    player.sendMessage("§6🏰 Ville §e" + cityName + " §6fondée !");
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
                    if (args.length < 2 || !args[1].equalsIgnoreCase("toggle")) {
                        player.sendMessage("§cUsage : /city quests toggle");
                        return true;
                    }
                    questHUD.toggle(player);
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
                        case "new" -> {
                            if (args.length < 3) {
                                player.sendMessage("§cUsage : /city build new <nom>");
                                return true;
                            }
                            if (!cityManager.isCityInitialized()) {
                                player.sendMessage("§c❌ Aucune ville n'existe encore.");
                                return true;
                            }
                            String name = args[2];
                            if (buildingManager.nameExists(name)) {
                                player.sendMessage("§c❌ Bâtiment §f" + name
                                        + " §cdéjà existant.");
                                return true;
                            }
                            buildingSession.setPendingName(player.getUniqueId(), name);
                            ItemStack stick = new ItemStack(BuildingListener.SELECTION_TOOL);
                            ItemMeta meta   = stick.getItemMeta();
                            meta.setDisplayName("§e🏗 Sélection : §f" + name);
                            meta.setLore(List.of(
                                    "§7Clic gauche §f: Coin 1",
                                    "§7Clic droit §f: Coin 2",
                                    "§7Les deux définis = bâtiment créé"
                            ));
                            stick.setItemMeta(meta);
                            player.getInventory().addItem(stick);
                            player.sendMessage("§a✅ Mode sélection activé pour §e"
                                    + name + "§a !");
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
                            CityNPC npc = Arrays.stream(CityNPC.values())
                                    .filter(n -> n.tag.replace("citycore_", "")
                                            .equalsIgnoreCase(args[3]))
                                    .findFirst().orElse(null);
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
                            buildingManager.assignNPC(bTarget.id(), npc.tag);
                            notificationManager.notifyAll(npc, getServer(), npcManager);
                            npcDataManager.setState(npc, NPCState.ASSIGNED);
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
                            buildingManager.createBuilding(
                                    pending.name(), pending.world(),
                                    pending.x1(), pending.z1(),
                                    pending.x2(), pending.z2(),
                                    null, null, null, null);
                            player.sendMessage("§a✅ Bâtiment §e" + pending.name()
                                    + " §acréé sans point NPC.");
                            buildingSession.clear(player.getUniqueId());
                            player.getInventory().setItemInMainHand(null);
                        }
                        default -> player.sendMessage(
                                "§cUsage : /city build [new|show|remove|assign|skip]");
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