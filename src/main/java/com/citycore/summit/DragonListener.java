package com.citycore.summit;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Joue le son "citycore:dragon" quand un joueur dépasse 400 blocs
 * d'altitude en chevauchant une entité (dragon custom ou autre).
 * Paramètres lus depuis config.yml (section "dragon").
 */
public class DragonListener implements Listener {

    private final JavaPlugin plugin;

    // ── Config ────────────────────────────────────────────────────────────────
    private boolean enabled;
    private int     minHeight;
    private String  soundKey;
    private float   soundVolume;
    private float   soundPitch;
    private long    cooldownMs;
    private String  message;

    // ── État interne ──────────────────────────────────────────────────────────
    /** Joueurs actuellement montés sur une entité */
    private final Set<UUID>          mountedPlayers = new HashSet<>();
    /** Cooldown par joueur */
    private final Map<UUID, Long>    lastTriggered  = new HashMap<>();
    /** Pour ne déclencher qu'à la montée (pas à chaque tick au-dessus de 400) */
    private final Map<UUID, Boolean> wasAbove       = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────

    public DragonListener(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Recharge la config (appelable via /citycore reload) */
    public void reload() {
        var cfg = plugin.getConfig();
        enabled     = cfg.getBoolean("dragon.enabled",           true);
        minHeight   = cfg.getInt    ("dragon.min-height",        400);
        soundKey    = cfg.getString ("dragon.sound-key",         "citycore:dragon");
        soundVolume = (float) cfg.getDouble("dragon.sound-volume", 1.0);
        soundPitch  = (float) cfg.getDouble("dragon.sound-pitch",  1.0);
        cooldownMs  = cfg.getLong   ("dragon.cooldown-seconds",  120) * 1000L;
        message     = cfg.getString ("dragon.message",
                "§5🐉 §dVous planez au-dessus des nuages !");

        plugin.getLogger().info("[Dragon] Rechargé — minHeight=" + minHeight
                + " | sound=" + soundKey
                + " | cooldown=" + (cooldownMs / 1000) + "s");
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Suit les montées/descentes de monture pour tenir mountedPlayers à jour.
     * EntityMountEvent est appelé pour TOUTE entité qui monte sur une autre,
     * on filtre sur le rider = Player.
     */
    @EventHandler
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            mountedPlayers.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            mountedPlayers.remove(uuid);
            wasAbove.put(uuid, false); // reset pour la prochaine monture
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;

        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        boolean above = event.getTo().getBlockY() >= minHeight;

        boolean was = wasAbove.getOrDefault(uuid, false);
        wasAbove.put(uuid, above);

        if (!above || was) return;

        long now  = System.currentTimeMillis();
        long last = lastTriggered.getOrDefault(uuid, 0L);
        if (now - last < cooldownMs) return;

        lastTriggered.put(uuid, now);

        playDragonSound(player);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void playDragonSound(Player player) {
        player.playSound(
                player.getLocation(),
                soundKey,
                org.bukkit.SoundCategory.MASTER,
                soundVolume,
                soundPitch
        );

        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }

        plugin.getLogger().info("[Dragon] Son joué pour " + player.getName()
                + " à Y=" + player.getLocation().getBlockY()
                + " | world=" + player.getWorld().getName()
                + " | X=" + player.getLocation().getBlockX()
                + " | Z=" + player.getLocation().getBlockZ()
                + " | sound=" + soundKey
                + " | volume=" + soundVolume
                + " | pitch=" + soundPitch);
    }

    // ── Nettoyage à la déconnexion ────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    public void cleanup(UUID uuid) {
        mountedPlayers.remove(uuid);
        lastTriggered.remove(uuid);
        wasAbove.remove(uuid);
    }
}