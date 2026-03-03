package com.citycore.chunk;

import com.citycore.city.CityManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChunkParticleTask {

    private final JavaPlugin plugin;
    private final CityManager cityManager;
    private final Player player;

    // Refresh toutes les 2 ticks → 10 passes/seconde, rendu continu sans clignotement
    private static final long REFRESH_TICKS = 2L;
    private static final double STEP = 1.0; // 1 point par bloc
    private static final int HEIGHT_RANGE = 10; // ±10 blocs autour du joueur
    private static final double PARTICLE_STEP_Y = 1.0; // 1 particule par bloc de hauteur

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
                drawExteriorBorders();
                elapsed += REFRESH_TICKS;
            }
        }.runTaskTimer(plugin, 0L, REFRESH_TICKS);
    }

    /**
     * Dessine uniquement les bordures extérieures :
     * une arête entre un chunk claimé et un chunk non-claimé.
     */
    private void drawExteriorBorders() {
        World world = player.getWorld();
        String worldName = world.getName();
        List<long[]> chunks = cityManager.getClaimedChunkCoords(worldName);

        // Construit un set rapide pour lookup O(1)
        Set<Long> claimedSet = new HashSet<>();
        for (long[] coords : chunks) {
            claimedSet.add(packCoords((int) coords[0], (int) coords[1]));
        }

        double playerY = player.getLocation().getY();
        double yMin = playerY - HEIGHT_RANGE;
        double yMax = playerY + HEIGHT_RANGE;

        for (long[] coords : chunks) {
            int cx = (int) coords[0];
            int cz = (int) coords[1];

            int x1 = cx * 16;
            int z1 = cz * 16;
            int x2 = x1 + 16;
            int z2 = z1 + 16;

            // Vérifie les 4 voisins : Nord(cz-1), Sud(cz+1), Ouest(cx-1), Est(cx+1)
            boolean noNorth = !claimedSet.contains(packCoords(cx, cz - 1));
            boolean noSouth = !claimedSet.contains(packCoords(cx, cz + 1));
            boolean noWest  = !claimedSet.contains(packCoords(cx - 1, cz));
            boolean noEast  = !claimedSet.contains(packCoords(cx + 1, cz));

            // Bordure Nord : z = z1, x de x1 à x2
            if (noNorth) {
                for (double x = x1; x <= x2; x += STEP) {
                    drawVerticalLine(world, x, z1, yMin, yMax);
                }
            }
            // Bordure Sud : z = z2, x de x1 à x2
            if (noSouth) {
                for (double x = x1; x <= x2; x += STEP) {
                    drawVerticalLine(world, x, z2, yMin, yMax);
                }
            }
            // Bordure Ouest : x = x1, z de z1 à z2
            if (noWest) {
                for (double z = z1; z <= z2; z += STEP) {
                    drawVerticalLine(world, x1, z, yMin, yMax);
                }
            }
            // Bordure Est : x = x2, z de z1 à z2
            if (noEast) {
                for (double z = z1; z <= z2; z += STEP) {
                    drawVerticalLine(world, x2, z, yMin, yMax);
                }
            }
        }
    }

    /**
     * Trace une ligne verticale de particules entre yMin et yMax.
     */
    private void drawVerticalLine(World world, double x, double z, double yMin, double yMax) {
        for (double y = yMin; y <= yMax; y += PARTICLE_STEP_Y) {
            spawnDust(world, x, y, z);
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

    /**
     * Encode deux ints en un long pour lookup rapide dans un HashSet.
     */
    private static long packCoords(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}