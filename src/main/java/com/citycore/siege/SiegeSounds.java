package com.citycore.siege;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sons des sièges (phase 4), au début, à la fin et aux moments clés seulement : cloche d'alarme de
 * la ville et cor de guerre venu du côté attaqué ; entrée du champion dans la bataille (cor venu
 * de lui) ; chute du porte-étendard ; victoire (cloches et feux d'artifice au-dessus de la mairie)
 * ou défaite. Aucun son d'ambiance pendant le combat (ni à chaque escouade qui arrive).
 */
final class SiegeSounds {

    private SiegeSounds() {}

    /** Un son pour chaque joueur, à sa position (entendu partout). */
    static void all(Sound sound, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), sound, volume, pitch);
    }

    /**
     * Un son « venu de » source : joué à 12 blocs du joueur dans sa direction (on l'entend de loin
     * et on sait d'où il vient) ; à sa vraie place s'il est plus près.
     */
    static void from(Location source, Sound sound, float volume, float pitch) {
        if (source == null) { all(sound, volume, pitch); return; }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(source.getWorld())) continue;
            Vector dir = source.toVector().subtract(p.getLocation().toVector());
            double d = dir.length();
            Location at = d < 12 ? source : p.getLocation().add(dir.multiply(12 / d));
            p.playSound(at, sound, volume, pitch);
        }
    }

    /** Cloche d'alarme : count coups, et la cloche de la mairie qui résonne. */
    static void bells(JavaPlugin plugin, int count, Location hall) {
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                all(Sound.BLOCK_BELL_USE, 1.2f, 0.9f);
                if (hall != null) hall.getWorld().playSound(hall, Sound.BLOCK_BELL_RESONATE, 2f, 1f);
            }, i * 14L);
        }
    }

    /** Alerte : cloches, puis cor de guerre du côté attaqué (cor de chèvre pour une armée, cor de raid pour des monstres). */
    static void alarm(JavaPlugin plugin, Location hall, Location enemy, boolean army) {
        bells(plugin, 3, hall);
        Bukkit.getScheduler().runTaskLater(plugin, () -> warHorn(enemy, army), 45L);
    }

    static void warHorn(Location enemy, boolean army) {
        from(enemy, army ? Sound.ITEM_GOAT_HORN_SOUND_2 : Sound.EVENT_RAID_HORN, 4f, army ? 0.9f : 1f);
    }

    /** Le porte-étendard tombe : bouclier brisé, fanfare aiguë. */
    static void standardFalls() {
        all(Sound.ITEM_SHIELD_BREAK, 0.8f, 0.8f);
        all(Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
    }

    /** Victoire : fanfare, cloches à toute volée, feux d'artifice au-dessus de la mairie. */
    static void victory(JavaPlugin plugin, Location hall) {
        all(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        bells(plugin, 5, hall);
        if (hall == null) return;
        for (int i = 0; i < 6; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> firework(hall), 10L + i * 12L);
        }
    }

    /** Défaite : grondement sourd, cloche qui résonne. */
    static void defeat(Location hall) {
        all(Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.6f);
        all(Sound.BLOCK_BELL_RESONATE, 1f, 0.7f);
        if (hall != null) hall.getWorld().playSound(hall, Sound.BLOCK_BELL_RESONATE, 2f, 0.7f);
    }

    /** Une fusée aux couleurs de Valmont (or, bleu, blanc), tirée au-dessus de la mairie, qui éclate très haut. */
    private static void firework(Location hall) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location at = hall.clone().add(rnd.nextDouble(-4, 4), 6, rnd.nextDouble(-4, 4));
        Color[] colors = {Color.fromRGB(0xF1CF5E), Color.fromRGB(0x3C6FD8), Color.WHITE, Color.fromRGB(0xC0392B)};
        FireworkEffect.Type[] types = {FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.STAR, FireworkEffect.Type.BURST};
        at.getWorld().spawn(at, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(types[rnd.nextInt(types.length)])
                    .withColor(colors[rnd.nextInt(colors.length)], colors[rnd.nextInt(colors.length)])
                    .withFade(Color.WHITE).trail(true).flicker(rnd.nextBoolean()).build());
            meta.setPower(2); // éclate une trentaine de blocs plus haut : personne n'est touché
            fw.setFireworkMeta(meta);
        });
    }
}
