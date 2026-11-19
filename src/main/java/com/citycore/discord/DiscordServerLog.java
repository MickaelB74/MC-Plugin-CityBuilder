package com.citycore.discord;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Set;

/**
 * Ce que le serveur lui-même raconte à Discord, en dehors de la vie de la ville :
 * <ul>
 *   <li><b>salon d'administration</b> : commandes des opérateurs et de la console, changements de
 *       mode de jeu ;</li>
 *   <li><b>salon de discussion</b> : connexions et déconnexions, et la commande {@code /discord
 *       <message>} qui envoie un message des joueurs vers Discord (le sens inverse, {@code /chat}
 *       sur Discord, est affiché en jeu par le mod compagnon).</li>
 * </ul>
 * Réglages : section {@code discord} de config.yml.
 */
public final class DiscordServerLog implements Listener, CommandExecutor {

    /** Commandes dont les arguments ne sont jamais recopiés (mots de passe). */
    private static final Set<String> SECRET = Set.of("login", "l", "log", "register", "reg",
            "changepassword", "changepw", "cp", "unregister", "premium", "2fa");
    /** Longueur maximale d'un message de /discord. */
    private static final int MAX_CHAT = 300;

    private final boolean opCommands, consoleCommands, gameModes, connections;

    public DiscordServerLog(JavaPlugin plugin) {
        ConfigurationSection cfg = plugin.getConfig();
        opCommands      = cfg.getBoolean("discord.admin.commandes-op", true);
        consoleCommands = cfg.getBoolean("discord.admin.commandes-console", true);
        gameModes       = cfg.getBoolean("discord.admin.modes-de-jeu", true);
        connections     = cfg.getBoolean("discord.chat.connexions", true);
    }

    /* ───────────────────────── Administration ───────────────────────── */

    /** Commande tapée par un opérateur (ou un administrateur de CityCore). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!opCommands) return;
        Player p = event.getPlayer();
        if (!p.isOp() && !p.hasPermission("citycore.admin")) return;
        String line = event.getMessage();
        if (isOwnChat(line)) return; // /discord : déjà dans le salon de discussion
        DiscordFeed.admin("admin.commande", p.getName(), masked(line));
    }

    /** Commande lancée depuis la console du serveur. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!consoleCommands) return;
        DiscordFeed.admin("admin.console", "Console", masked("/" + event.getCommand()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (!gameModes) return;
        Player p = event.getPlayer();
        if (p.hasMetadata("NPC")) return; // PNJ de Citizens
        DiscordFeed.admin("admin.mode", p.getName(),
                "passe en mode " + modeName(event.getNewGameMode()) + " (était en " + modeName(p.getGameMode()) + ")");
    }

    /* ───────────────────────── Discussion ───────────────────────── */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (connections) DiscordFeed.chat("chat.connexion", event.getPlayer().getName(), "a rejoint le serveur");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (connections) DiscordFeed.chat("chat.deconnexion", event.getPlayer().getName(), "a quitté le serveur");
    }

    /** {@code /discord <message>} : le message part sur Discord et s'affiche aussi en jeu. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DiscordFeed.active()) {
            sender.sendMessage("§c❌ Le lien avec Discord est désactivé sur ce serveur.");
            return true;
        }
        String message = String.join(" ", args).trim();
        if (message.isEmpty()) {
            sender.sendMessage("§7Usage : §f/discord <message> §8— envoie un message dans le salon de discussion Discord.");
            return true;
        }
        if (message.length() > MAX_CHAT) message = message.substring(0, MAX_CHAT) + "…";
        String name = sender instanceof Player p ? p.getName() : "Console";
        DiscordFeed.chat("chat.message", name, message);
        org.bukkit.Bukkit.broadcastMessage("§9[Discord] §f" + name + " §7: §f" + message);
        if (!DiscordFeed.linked()) {
            sender.sendMessage("§8Le bot Discord ne répond pas pour l'instant : le message sera transmis à son retour.");
        }
        return true;
    }

    /* ───────────────────────── Outils ───────────────────────── */

    private static boolean isOwnChat(String line) {
        String name = commandName(line);
        return name.equals("discord") || name.equals("citycore:discord");
    }

    /** « /Login secret » → « login ». */
    private static String commandName(String line) {
        String s = line.startsWith("/") ? line.substring(1) : line;
        int space = s.indexOf(' ');
        return (space < 0 ? s : s.substring(0, space)).toLowerCase(Locale.ROOT);
    }

    /** La commande telle qu'elle a été tapée, arguments masqués pour les commandes à mot de passe. */
    private static String masked(String line) {
        String name = commandName(line);
        int colon = name.indexOf(':');
        if (SECRET.contains(colon < 0 ? name : name.substring(colon + 1))) return "/" + name + " ***";
        return line.length() > 400 ? line.substring(0, 400) + "…" : line;
    }

    private static String modeName(GameMode mode) {
        return switch (mode) {
            case CREATIVE  -> "créatif";
            case SURVIVAL  -> "survie";
            case ADVENTURE -> "aventure";
            case SPECTATOR -> "spectateur";
        };
    }
}
