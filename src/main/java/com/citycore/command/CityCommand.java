package com.citycore.command;

import com.citycore.building.*;
import com.citycore.city.City;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import com.citycore.npc.villager.VillagerGUI;
import com.citycore.quest.QuestHUD;
import com.citycore.util.ChunkParticleTask;
import com.citycore.city.CityManager;
import net.milkbowl.vault.economy.Economy;
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

public class CityCommand implements CommandExecutor {

    private final CityManager cityManager;
    private final JavaPlugin plugin;
    private Economy economy;
    private final NPCManager npcManager;
    private final NPCDataManager npcDataManager;
    private final QuestHUD questHUD;
    private final BuildingManager buildingManager;
    private final BuildingSession buildingSession;
    private final BuildingGUI buildingGUI;
    private final BuildingBorderTask buildingBorderTask;

    public CityCommand(CityManager cityManager, NPCManager npcManager, JavaPlugin plugin, NPCDataManager npcDataManager,QuestHUD questHUD, BuildingManager buildingManager, BuildingSession buildingSession, BuildingGUI buildingGUI, BuildingBorderTask buildingBorderTask) {
        this.cityManager = cityManager;
        this.npcManager = npcManager;
        this.plugin = plugin;
        this.npcDataManager = npcDataManager;
        this.questHUD       = questHUD;
        this.buildingManager = buildingManager;
        this.buildingSession = buildingSession;
        this.buildingGUI = buildingGUI;
        this.buildingBorderTask = buildingBorderTask;
        setupEconomy();
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        } else {
            plugin.getLogger().warning("Vault/Economy introuvable — /city deposit désactivé.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande joueur uniquement.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        CitySubCommand sub = CitySubCommand.fromLabel(args[0]);
        if (sub == null) {
            sendHelp(player);
            return true;
        }

        try {
            switch (sub) {

                case CREATE -> {
                    if (cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ La ville existe déjà.");
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage("§cUsage : /city " + CitySubCommand.CREATE.usage);
                        return true;
                    }
                    String cityName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    cityManager.initializeCity(cityName);
                    cityManager.claimChunk(player.getLocation().getChunk());

                    // Spawn le maire 2 blocs devant le joueur
                    npcManager.spawnMayor(player);

                    player.sendMessage("§6🏰 Ville §e" + cityName + " §6fondée !");
                    player.sendMessage("§a📍 Chunk de départ claim.");
                    player.sendMessage("§7👴 Le §6Maire §7est apparu devant vous.");
                }

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
                        player.sendMessage("§c❌ Limite atteinte §7(§f" + cityManager.getClaimedChunkCount() + "§7/§f" + cityManager.getMaxChunks() + "§7).");
                        player.sendMessage("§7💡 §e/city expand §7pour +1 slot · prix : §6" + price + " coins");
                        return true;
                    }
                    cityManager.claimChunk(chunk);
                    player.sendMessage("§a✅ Chunk claimé ! §7(§f" + cityManager.getClaimedChunkCount() + "§7/§f" + cityManager.getMaxChunks() + "§7)");
                }

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
                    // Empêche de unclaim le dernier chunk (la ville doit garder au moins 1)
                    if (cityManager.getClaimedChunkCount() <= 1) {
                        player.sendMessage("§c❌ Impossible de retirer le dernier chunk de la ville.");
                        return true;
                    }
                    cityManager.unclaimChunk(chunk);
                    player.sendMessage("§e🗑 Chunk retiré de la ville. §7(§f" + cityManager.getClaimedChunkCount() + "§7/§f" + cityManager.getMaxChunks() + "§7)");
                }

                case EXPAND -> {
                    // Op uniquement — les autres doivent passer par le maire
                    if (!player.isOp()) {
                        player.sendMessage("§c❌ Parlez au §6Maire §cpour agrandir la ville.");
                        return true;
                    }
                    if (!cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    int price = cityManager.getNextExpandPrice();
                    int balance = cityManager.getCityCoins();
                    player.sendMessage("§7Prix : §6" + price + " coins §7· Caisse : §6" + balance + " coins");

                    CityManager.ExpandResult result = cityManager.expandMaxChunks();
                    if (result.success()) {
                        player.sendMessage("§a✅ Capacité étendue ! §7Max chunks : §f" + result.newMaxChunks());
                        player.sendMessage("§7Caisse restante : §6" + result.newBalance() + " coins");
                    } else {
                        player.sendMessage("§c❌ Fonds insuffisants. Il manque §f" + (price - balance) + " coins.");
                    }
                }

                case DEPOSIT -> {
                    if (!cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    if (economy == null) {
                        player.sendMessage("§c❌ Vault non disponible sur ce serveur.");
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
                        double playerBalance = economy.getBalance(player);
                        player.sendMessage("§c❌ Vous n'avez pas assez d'argent.");
                        player.sendMessage("§7Votre solde : §6" + (int) playerBalance + " §7· Demandé : §6" + amount);
                        return true;
                    }
                    // Débit joueur → crédit caisse ville
                    economy.withdrawPlayer(player, amount);
                    int newCityBalance = cityManager.addCityCoins(amount);
                    player.sendMessage("§a✅ §6" + amount + " coins §adéposés dans la caisse de la ville.");
                    player.sendMessage("§7Caisse ville : §6" + newCityBalance + " §7· Votre solde : §6" + (int) economy.getBalance(player));
                }

                case MAP -> {
                    if (!cityManager.isCityInitialized()) {
                        player.sendMessage("§c❌ Aucune ville fondée.");
                        return true;
                    }
                    new ChunkParticleTask(plugin, cityManager, player).runForSeconds(5);
                    player.sendMessage("§b🗺 Bordures affichées pendant §f5 secondes§b.");
                }

                case INFO -> {
                    // Accessible à tous, avec ou sans maire
                    City city = cityManager.getCity();
                    if (city == null) {
                        player.sendMessage("§c❌ Aucune ville fondée. Utilisez /city create <nom>");
                        return true;
                    }
                    player.sendMessage("§8§m--------------------");
                    player.sendMessage("§6 " + city.getName());
                    player.sendMessage("§8§m--------------------");
                    player.sendMessage("§eNiveau  : §f" + city.getLevel());
                    player.sendMessage("§eCaisse  : §6" + city.getCoins() + " coins");
                    player.sendMessage("§eChunks  : §f" + city.getClaimedChunks() + " §7/ §f" + city.getMaxChunks());
                    player.sendMessage("§eExpand  : §6" + cityManager.getNextExpandPrice() + " coins §7pour +1 slot");
                    player.sendMessage("§8§m--------------------");
                }

                case QUESTS -> {
                    if (args.length < 2 || !args[1].equalsIgnoreCase("toggle")) {
                        player.sendMessage("§cUsage : /city quests toggle");
                        return true;
                    }
                    questHUD.toggle(player);
                }

                case NPC -> {
                    if (args.length < 3) {
                        player.sendMessage("§cUsage : /city npc <type> <spawn|levelUp|levelDown>");
                        return true;
                    }

                    // Résout le type NPC depuis args[1]
                    CityNPC target = Arrays.stream(CityNPC.values())
                            .filter(n -> n != CityNPC.MAYOR)
                            .filter(n -> n.tag.replace("citycore_", "").equalsIgnoreCase(args[1]))
                            .findFirst()
                            .orElse(null);

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
                                player.sendMessage("§c❌ Commande réservée aux administrateurs.");
                                return true;
                            }
                            if (npcManager.getNPC(target) != null) {
                                player.sendMessage("§c❌ " + target.displayName + " §cexiste déjà.");
                                return true;
                            }
                            npcManager.spawnNPC(target, spawnLocation(player));
                            player.sendMessage("§a✅ " + target.displayName + " §aest apparu !");
                        }

                        case "levelup" -> {
                            if (!player.isOp()) return false;
                            int currentLevel = npcDataManager.getLevel(target);
                            if (currentLevel >= 5) {
                                player.sendMessage("§c❌ " + target.displayName + " §cest déjà au niveau maximum.");
                                return true;
                            }
                            Map<Integer, Integer> thresholds = loadThresholds(target);
                            int newLevel = currentLevel + 1;
                            npcDataManager.setLevel(target, newLevel, thresholds);
                            player.sendMessage("§a✅ §e" + target.displayName
                                    + " §apassé au niveau §e"
                                    + VillagerGUI.getLevelName(newLevel) + "§a.");
                        }

                        case "leveldown" -> {
                            if (!player.isOp()) return false;
                            int currentLevel = npcDataManager.getLevel(target);
                            if (currentLevel <= 1) {
                                player.sendMessage("§c❌ " + target.displayName + " §cest déjà au niveau minimum.");
                                return true;
                            }
                            Map<Integer, Integer> thresholds = loadThresholds(target);
                            int newLevel = currentLevel - 1;
                            npcDataManager.setLevel(target, newLevel, thresholds);
                            player.sendMessage("§a✅ §e" + target.displayName
                                    + " §arepassé au niveau §e"
                                    + VillagerGUI.getLevelName(newLevel) + "§a.");
                        }

                        default -> player.sendMessage(
                                "§c❌ Action inconnue. Disponibles : §fspawn§c, §flevelUp§c, §flevelDown");
                    }
                }

                case BUILD -> {
                    // /city build — GUI
                    if (args.length == 1) {
                        buildingGUI.open(player);
                        return true;
                    }

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
                                player.sendMessage("§c❌ Un bâtiment nommé §f" + name
                                        + " §cexiste déjà.");
                                return true;
                            }
                            buildingSession.setPendingName(player.getUniqueId(), name);

                            ItemStack stick = new ItemStack(BuildingListener.SELECTION_TOOL);
                            ItemMeta meta   = stick.getItemMeta();
                            meta.setDisplayName("§e🏗 Sélection bâtiment : §f" + name);
                            meta.setLore(List.of(
                                    "§7Clic gauche §f: Coin 1",
                                    "§7Clic droit §f: Coin 2",
                                    "§7Les deux coins définis = bâtiment créé"
                            ));
                            stick.setItemMeta(meta);
                            player.getInventory().addItem(stick);

                            player.sendMessage("§a✅ Mode sélection activé pour §e" + name + "§a !");
                            player.sendMessage("§7§oClic gauche = Coin 1 | Clic droit = Coin 2");
                        }

                        case "show" -> buildingBorderTask.toggle(player);

                        case "remove" -> {
                            if (args.length < 3) {
                                player.sendMessage("§cUsage : /city build remove <nom|all>");
                                return true;
                            }

                            if (args[2].equalsIgnoreCase("all")) {
                                if (!player.isOp()) {
                                    player.sendMessage("§c❌ Commande réservée aux administrateurs.");
                                    return true;
                                }
                                buildingManager.removeAll();
                                player.sendMessage("§a✅ Tous les bâtiments ont été supprimés.");
                                return true;
                            }

                            String name = args[2];
                            if (!buildingManager.nameExists(name)) {
                                player.sendMessage("§c❌ Aucun bâtiment nommé §f" + name + "§c.");
                                return true;
                            }
                            buildingManager.removeByName(name);
                            player.sendMessage("§a✅ Bâtiment §e" + name + " §asupprimé.");
                        }

                        case "assign" -> {
                            // /city build assign <nom_batiment> <npc_type>
                            if (args.length < 4) {
                                player.sendMessage(
                                        "§cUsage : /city build assign <bâtiment> <npc>");
                                return true;
                            }

                            String buildingName = args[2];
                            String npcType      = args[3];

                            // Vérifie que le bâtiment existe
                            Building target = buildingManager.getAllBuildings().stream()
                                    .filter(b -> b.name().equalsIgnoreCase(buildingName))
                                    .findFirst().orElse(null);

                            if (target == null) {
                                player.sendMessage("§c❌ Bâtiment §f" + buildingName
                                        + " §cintrouvable.");
                                return true;
                            }

                            // Vérifie que le NPC existe
                            CityNPC npc = Arrays.stream(CityNPC.values())
                                    .filter(n -> n.tag.replace("citycore_", "")
                                            .equalsIgnoreCase(npcType))
                                    .findFirst().orElse(null);

                            if (npc == null) {
                                player.sendMessage("§c❌ NPC §f" + npcType + " §cintrouvable.");
                                return true;
                            }

                            buildingManager.assignNPC(target.id(), npc.tag);
                            player.sendMessage("§a✅ §e" + npc.displayName
                                    + " §aassigné au bâtiment §e" + target.name() + "§a !");
                        }

                        default -> player.sendMessage(
                                "§cUsage : /city build [new <nom>|show|remove <nom|all>]");
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