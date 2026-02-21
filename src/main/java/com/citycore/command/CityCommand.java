package com.citycore.command;

import com.citycore.city.City;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import com.citycore.util.ChunkParticleTask;
import com.citycore.city.CityManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class CityCommand implements CommandExecutor {

    private final CityManager cityManager;
    private final JavaPlugin plugin;
    private Economy economy;
    private final NPCManager npcManager;

    public CityCommand(CityManager cityManager, NPCManager npcManager, JavaPlugin plugin) {
        this.cityManager = cityManager;
        this.npcManager = npcManager;
        this.plugin = plugin;
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

        CitySubCommand sub = CitySubCommand.from(args[0]);
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

                case SPAWN -> {
                    if (!player.isOp()) {
                        player.sendMessage("§c❌ Commande réservée aux administrateurs.");
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage("§cUsage : /city spawn <type>");
                        player.sendMessage("§7Types disponibles : §estonemason");
                        return true;
                    }

                    switch (args[1].toLowerCase()) {
                        case "stonemason" -> {
                            if (npcManager.getNPC(CityNPC.STONEMASON) != null) {
                                player.sendMessage("§c❌ Le Tailleur de pierre existe déjà.");
                                return true;
                            }
                            Location loc = player.getLocation().clone();
                            loc.add(loc.getDirection().normalize().multiply(2));
                            loc.setY(Math.floor(loc.getY() + 1));
                            Location npcLoc = loc.clone();
                            npcLoc.setYaw((player.getLocation().getYaw() + 180) % 360);
                            npcLoc.setPitch(0);
                            npcManager.spawnNPC(CityNPC.STONEMASON, npcLoc);
                            player.sendMessage("§a✅ Brennan le Tailleur de pierre est apparu !");
                        }

                        case "jacksparrow" -> {
                            if (npcManager.getNPC(CityNPC.JACKSPARROW) != null) {
                                player.sendMessage("§c❌ Jack Sparrow existe déjà.");
                                return true;
                            }
                            Location loc = player.getLocation().clone();
                            loc.add(loc.getDirection().normalize().multiply(2));
                            loc.setY(Math.floor(loc.getY() + 1));
                            Location npcLoc = loc.clone();
                            npcLoc.setYaw((player.getLocation().getYaw() + 180) % 360);
                            npcLoc.setPitch(0);
                            npcManager.spawnNPC(CityNPC.JACKSPARROW, npcLoc);
                            player.sendMessage("§a✅ Jack Sparrow est apparu !");
                        }
                        default -> player.sendMessage("§c❌ Type inconnu. Disponibles : §estonemason§c, §ejacksparrow");
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
}