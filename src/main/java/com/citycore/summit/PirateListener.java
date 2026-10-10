package com.citycore.summit;

import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Joue le son "citycore:pirate" quand un joueur est sur un bateau,
 * regarde vers l'ouest (coucher de soleil) pendant l'heure du coucher.
 * Paramètres lus depuis config.yml (section "pirate").
 */
public class PirateListener implements Listener {

    private final JavaPlugin plugin;

    // ── Config ────────────────────────────────────────────────────────────────
    private boolean enabled;
    private String  soundKey;
    private float   soundVolume;
    private float   soundPitch;
    private long    cooldownMs;
    private String  message;

    /** Heure in-game du coucher (défaut : 11500–13000 sur 24000) */
    private long timeStart;
    private long timeEnd;

    /** Tolérance angulaire autour de l'ouest (270°), en degrés */
    private float yawTolerance;

    // ── État interne ──────────────────────────────────────────────────────────
    private final Map<UUID, Long>    lastTriggered = new HashMap<>();
    private final Map<UUID, Boolean> wasTriggered  = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────

    public PirateListener(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var cfg = plugin.getConfig();
        enabled      = cfg.getBoolean("pirate.enabled",           true);
        soundKey     = cfg.getString ("pirate.sound-key",         "citycore:pirate");
        soundVolume  = (float) cfg.getDouble("pirate.sound-volume", 1.0);
        soundPitch   = (float) cfg.getDouble("pirate.sound-pitch",  1.0);
        cooldownMs   = cfg.getLong   ("pirate.cooldown-seconds",  180) * 1000L;
        message      = cfg.getString ("pirate.message",           "§6☀ §eL'horizon rougit sous vos yeux, capitaine !");
        timeStart    = cfg.getLong   ("pirate.time-start",        11500);
        timeEnd      = cfg.getLong   ("pirate.time-end",          13000);
        yawTolerance = (float) cfg.getDouble("pirate.yaw-tolerance", 30.0);

        plugin.getLogger().info("[Pirate] Rechargé — sound=" + soundKey
                + " | time=" + timeStart + "-" + timeEnd
                + " | yawTolerance=±" + yawTolerance + "°"
                + " | cooldown=" + (cooldownMs / 1000) + "s");
    }

    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // ── 1. Le joueur doit être sur un bateau ──────────────────────────────
        if (!(player.getVehicle() instanceof Boat)) return;

        // ── 2. Heure in-game : coucher de soleil ──────────────────────────────
        World world    = player.getWorld();
        long  timeOfDay = world.getTime(); // 0-23999
        if (timeOfDay < timeStart || timeOfDay > timeEnd) {
            wasTriggered.put(uuid, false); // reset si hors créneau
            return;
        }

        // ── 3. Orientation : regarder vers l'ouest (yaw ≈ 90°) ───────────────
        // Bukkit : yaw 0° = Sud, 90° = Ouest, 180° = Nord, 270° = Est
        float yaw         = ((player.getLocation().getYaw() % 360) + 360) % 360;
        float westYaw     = 90.0f;
        float diff        = Math.abs(yaw - westYaw);
        if (diff > 180) diff = 360 - diff; // angle minimal

        if (diff > yawTolerance) {
            wasTriggered.put(uuid, false);
            return;
        }

        // ── 4. Ne déclencher qu'une fois par entrée dans la condition ─────────
        boolean was = wasTriggered.getOrDefault(uuid, false);
        wasTriggered.put(uuid, true);
        if (was) return;

        // ── 5. Cooldown ───────────────────────────────────────────────────────
        long now  = System.currentTimeMillis();
        long last = lastTriggered.getOrDefault(uuid, 0L);
        if (now - last < cooldownMs) return;

        lastTriggered.put(uuid, now);

        playPirateSound(player);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void playPirateSound(Player player) {
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

        plugin.getLogger().info("[Pirate] Son joué pour " + player.getName()
                + " | time=" + player.getWorld().getTime()
                + " | yaw=" + String.format("%.1f", player.getLocation().getYaw())
                + " | world=" + player.getWorld().getName());
    }

    // ── Nettoyage à la déconnexion ────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    public void cleanup(UUID uuid) {
        lastTriggered.remove(uuid);
        wasTriggered.remove(uuid);
    }
}