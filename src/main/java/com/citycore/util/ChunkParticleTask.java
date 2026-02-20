package com.citycore.util;

import com.citycore.city.CityManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class ChunkParticleTask {

    private final JavaPlugin plugin;
    private final CityManager cityManager;
    private final Player player;

    // Refresh toutes les 2 ticks → 10 passes/seconde, rendu continu sans clignotement
    private static final long REFRESH_TICKS = 2L;
    private static final double STEP = 1.0; // 1 point par bloc (moins dense = moins de lag)
    private static final Particle.DustOptions DUST = new Particle.DustOptions(
            Color.fromRGB(255, 215, 0), 0.8f // Or, taille 0.8
    );

    public ChunkParticleTask(JavaPlugin plugin, CityManager cityManager, Player player) {
        this.plugin = plugin;
        this.cityManager = cityManager;
        this.player = player;
    }

    /**
     * Affiche les bordures en continu pendant 'seconds' secondes.
     * Refresh à 2 ticks pour éviter tout clignotement.
     */
    public void runForSeconds(int seconds) {
        long totalTicks = seconds * 20L;

        new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= totalTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                drawAllBorders();
                elapsed += REFRESH_TICKS;
            }
        }.runTaskTimer(plugin, 0L, REFRESH_TICKS);
    }

    private void drawAllBorders() {
        World world = player.getWorld();
        double y = player.getLocation().getY();
        List<long[]> chunks = cityManager.getClaimedChunkCoords(world.getName());

        for (long[] coords : chunks) {
            drawChunkBorder(world, (int) coords[0], (int) coords[1], y);
        }
    }

    private void drawChunkBorder(World world, int cx, int cz, double y) {
        int x1 = cx * 16;
        int z1 = cz * 16;
        int x2 = x1 + 16;
        int z2 = z1 + 16;

        // Deux rangées de hauteur (y et y+1) pour que la bordure soit visible
        for (double h : new double[]{y, y + 1.0}) {
            // Nord (z fixe = z1), Sud (z fixe = z2)
            for (double x = x1; x <= x2; x += STEP) {
                spawnDust(world, x, h, z1);
                spawnDust(world, x, h, z2);
            }
            // Ouest (x fixe = x1), Est (x fixe = x2)
            for (double z = z1; z <= z2; z += STEP) {
                spawnDust(world, x1, h, z);
                spawnDust(world, x2, h, z);
            }
        }
    }

    private void spawnDust(World world, double x, double y, double z) {
        world.spawnParticle(
                Particle.DUST,
                new Location(world, x, y, z),
                1, 0, 0, 0, 0,
                DUST
        );
    }
}