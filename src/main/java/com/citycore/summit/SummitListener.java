package com.citycore.summit;

import com.citycore.quest.QuestListener;
import org.bukkit.World;
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
 * Détecte quand un joueur atteint un sommet et joue un son custom
 * uniquement pour ce joueur. Paramètres lus depuis config.yml.
 */
public class SummitListener implements Listener {

    private final JavaPlugin    plugin;
    private final QuestListener questListener; // null si non encore initialisé

    // ── Valeurs chargées depuis config.yml ───────────────────────────────────
    private int     minHeight;
    private int     scanRadius;
    private String  soundKey;
    private float   soundVolume;
    private float   soundPitch;
    private long    cooldownMs;
    private String  message;
    private boolean enabled;

    // ── État interne ─────────────────────────────────────────────────────────
    private final Map<UUID, Long>    lastTriggered = new HashMap<>();
    private final Map<UUID, Boolean> wasOnSummit   = new HashMap<>();

    /**
     * Constructeur sans QuestListener — pour les cas où il n'est pas encore disponible.
     * Utiliser setQuestListener() ensuite.
     */
    public SummitListener(JavaPlugin plugin) {
        this(plugin, null);
    }

    /**
     * Constructeur principal — à utiliser depuis CityCore après avoir
     * créé le QuestListener.
     */
    public SummitListener(JavaPlugin plugin, QuestListener questListener) {
        this.plugin        = plugin;
        this.questListener = questListener;
        reload();
    }

    /** Recharge les valeurs depuis config.yml (appelable via /citycore reload) */
    public void reload() {
        var cfg = plugin.getConfig();
        enabled     = cfg.getBoolean("summit.enabled",          true);
        minHeight   = cfg.getInt    ("summit.min-height",       150);
        scanRadius  = cfg.getInt    ("summit.scan-radius",      10);
        soundKey    = cfg.getString ("summit.sound-key",        "citycore:summit_theme");
        soundVolume = (float) cfg.getDouble("summit.sound-volume", 1.0);
        soundPitch  = (float) cfg.getDouble("summit.sound-pitch",  1.0);
        cooldownMs  = cfg.getLong   ("summit.cooldown-seconds", 60) * 1000L;
        message     = cfg.getString ("summit.message",          "§b⛰ §eVous avez atteint un sommet !");

        plugin.getLogger().info("[Summit] Rechargé — minHeight=" + minHeight
                + " | scanRadius=" + scanRadius
                + " | sound=" + soundKey
                + " | cooldown=" + (cooldownMs / 1000) + "s");
    }

    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;

        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // ── Contrainte : le joueur doit être à pied ───────────────────────────
        if (player.isGliding())              return; // élytre
        if (player.isFlying())               return; // vol créatif / spectateur
        if (player.getVehicle() != null)     return; // monture (cheval, bateau, dragon…)
        if (player.isSwimming())             return; // nage

        boolean onSummit = isSummit(
                event.getTo().getBlockX(),
                event.getTo().getBlockY(),
                event.getTo().getBlockZ(),
                event.getTo().getWorld()
        );

        boolean wasOn = wasOnSummit.getOrDefault(uuid, false);
        wasOnSummit.put(uuid, onSummit);

        if (!onSummit || wasOn) return;

        long now  = System.currentTimeMillis();
        long last = lastTriggered.getOrDefault(uuid, 0L);
        if (now - last < cooldownMs) return;

        lastTriggered.put(uuid, now);

        playSummitSound(player);

        if (questListener != null) {
            questListener.onSummitReached(player);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    public boolean isSummit(int px, int py, int pz, World world) {
        if (py < minHeight) return false;

        int maxY = world.getMaxHeight();
        int r2   = scanRadius * scanRadius;

        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                if (dx * dx + dz * dz > r2) continue; // forme circulaire
                if (dx == 0 && dz == 0)     continue; // ignorer la colonne du joueur

                for (int y = py; y <= maxY; y++) {
                    if (!world.getBlockAt(px + dx, y, pz + dz).getType().isAir()) {
                        return false; // un bloc existe à cette hauteur dans le rayon
                    }
                }
            }
        }
        return true;
    }

    private void playSummitSound(Player player) {
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

        plugin.getLogger().info("[Summit] Son joué pour " + player.getName()
                + " à Y=" + player.getLocation().getBlockY());
    }

    // ── Nettoyage à la déconnexion ────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    public void cleanup(UUID uuid) {
        lastTriggered.remove(uuid);
        wasOnSummit.remove(uuid);
    }
}