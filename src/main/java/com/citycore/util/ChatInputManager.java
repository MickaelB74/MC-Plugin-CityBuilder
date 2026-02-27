package com.citycore.util;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Système générique de saisie par chat.
 * Usage :
 *   ChatInputManager.prompt(player, "Entrez un montant :", input -> { ... });
 */
public class ChatInputManager implements Listener {

    private static ChatInputManager instance;

    private final Map<UUID, Consumer<String>> pending = new HashMap<>();

    private ChatInputManager() {}

    public static void init(JavaPlugin plugin) {
        instance = new ChatInputManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    /**
     * Demande une saisie au joueur via le chat.
     *
     * @param player   joueur concerné
     * @param prompt   message affiché avant la saisie
     * @param callback appelé avec la valeur saisie (annulé si "annuler")
     */
    public static void prompt(Player player, String prompt, Consumer<String> callback) {
        if (instance == null) throw new IllegalStateException("ChatInputManager non initialisé !");
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(prompt);
        player.sendMessage("§7(Tapez §cannuler §7pour annuler)");
        player.sendMessage("");
        instance.pending.put(player.getUniqueId(), callback);
    }

    /** Annule une saisie en attente pour un joueur. */
    public static void cancel(Player player) {
        if (instance != null) instance.pending.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = pending.remove(player.getUniqueId());
        if (callback == null) return;

        event.setCancelled(true); // n'affiche pas le message dans le chat
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            player.sendMessage("§7Saisie annulée.");
            return;
        }

        // Le callback s'exécute sur le thread principal
        player.getServer().getScheduler().runTask(
                player.getServer().getPluginManager().getPlugin("CityCore"),
                () -> callback.accept(input)
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}