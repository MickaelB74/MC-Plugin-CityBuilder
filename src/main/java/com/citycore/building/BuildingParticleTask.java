package com.citycore.building;

import com.citycore.building.Building;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class BuildingParticleTask {

    private final JavaPlugin plugin;
    private final Player     player;
    private final Building   building;

    private static final long   REFRESH_TICKS  = 2L;
    private static final double STEP           = 1.0;
    private static final int    HEIGHT_RANGE   = 10;
    private static final double PARTICLE_STEP_Y = 1.0;

    private static final Particle.DustOptions DUST = new Particle.DustOptions(
            Color.fromRGB(0, 150, 255), 0.8f // Bleu, taille 0.8
    );

    public BuildingParticleTask(JavaPlugin plugin, Player player, Building building) {
        this.plugin   = plugin;
        this.player   = player;
        this.building = building;
    }

    /**
     * Affiche les bordures du bâtiment pendant 'seconds' secondes.
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
                drawBuildingBorder();
                elapsed += REFRESH_TICKS;
            }
        }.runTaskTimer(plugin, 0L, REFRESH_TICKS);
    }

    /**
     * Dessine les 4 murs de bordure du bâtiment sur ±10 blocs autour du joueur.
     */
    private void drawBuildingBorder() {
        World world = player.getWorld();

        // Vérifie que le joueur est dans le même monde que le bâtiment
        if (!world.getName().equals(building.world())) return;

        double playerY = player.getLocation().getY();
        double yMin    = playerY - HEIGHT_RANGE;
        double yMax    = playerY + HEIGHT_RANGE;

        int x1 = building.x1();
        int z1 = building.z1();
        int x2 = building.x2();
        int z2 = building.z2();

        // Bordure Nord : z = z1
        for (double x = x1; x <= x2; x += STEP) {
            drawVerticalLine(world, x, z1, yMin, yMax);
        }
        // Bordure Sud : z = z2
        for (double x = x1; x <= x2; x += STEP) {
            drawVerticalLine(world, x, z2, yMin, yMax);
        }
        // Bordure Ouest : x = x1
        for (double z = z1; z <= z2; z += STEP) {
            drawVerticalLine(world, x1, z, yMin, yMax);
        }
        // Bordure Est : x = x2
        for (double z = z1; z <= z2; z += STEP) {
            drawVerticalLine(world, x2, z, yMin, yMax);
        }
    }

    private void drawVerticalLine(World world, double x, double z, double yMin, double yMax) {
        for (double y = yMin; y <= yMax; y += PARTICLE_STEP_Y) {
            world.spawnParticle(
                    Particle.DUST,
                    new Location(world, x, y, z),
                    1, 0, 0, 0, 0,
                    DUST
            );
        }
    }
}