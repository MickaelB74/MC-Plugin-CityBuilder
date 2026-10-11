package com.citycore.building;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Affiche quelques secondes le contour d'un bâtiment, pour le joueur seul : les arêtes de la
 * boîte qui l'enveloppe, posées sur la face EXTÉRIEURE de ses blocs (une zone de x1 à x2 va de la
 * coordonnée x1 à x2 + 1). Arêtes du bas et du haut (hauteur du bâtiment), une ceinture à hauteur
 * des yeux du joueur, et les quatre arêtes verticales des coins.
 */
public class BuildingParticleTask {

    private final JavaPlugin plugin;
    private final Player     player;
    private final Building   building;

    private static final long   REFRESH_TICKS = 5L;
    /** Écart entre deux particules d'une arête (agrandi pour les très grands bâtiments). */
    private static final double MIN_STEP      = 0.5;
    /** Nombre maximal de particules par arête horizontale. */
    private static final int    MAX_POINTS    = 160;

    private static final Particle.DustOptions DUST = new Particle.DustOptions(
            Color.fromRGB(0, 150, 255), 1.0f // Bleu
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

    /** Arêtes de la boîte du bâtiment, sur l'extérieur de ses blocs. */
    private void drawBuildingBorder() {
        World world = player.getWorld();
        if (!world.getName().equals(building.world())) return;

        // Faces extérieures : le bloc x2 s'étend jusqu'à x2 + 1
        double minX = building.x1(), maxX = building.x2() + 1;
        double minZ = building.z1(), maxZ = building.z2() + 1;

        // Du sol de définition au sommet de ce qui est construit (pas toute la zone de recherche,
        // qui descend sous terre et monte très haut) ; sinon une tranche autour du joueur
        double playerY = player.getLocation().getY();
        if (top == null) top = builtTop(world);
        double bottom = building.refY() != null ? building.refY()
                : building.hasHeight() ? building.yMin() : Math.floor(playerY) - 2;
        double top = Math.max(bottom + 3, this.top);
        double eyes   = Math.max(bottom, Math.min(top, player.getEyeLocation().getY()));

        double step = Math.max(MIN_STEP, Math.max(maxX - minX, maxZ - minZ) / MAX_POINTS);
        rectangle(world, minX, maxX, minZ, maxZ, bottom, step);
        rectangle(world, minX, maxX, minZ, maxZ, top, step);
        if (eyes - bottom > 1 && top - eyes > 1) rectangle(world, minX, maxX, minZ, maxZ, eyes, step);

        // Arêtes verticales aux quatre coins
        for (double y = bottom; y <= top; y += MIN_STEP) {
            dot(world, minX, y, minZ);
            dot(world, maxX, y, minZ);
            dot(world, minX, y, maxZ);
            dot(world, maxX, y, maxZ);
        }
    }

    /** Sommet : bloc le plus haut de la zone (calculé une fois), sans dépasser la zone de recherche. */
    private Double top;

    private double builtTop(World world) {
        int highest = Integer.MIN_VALUE;
        for (int x = building.x1(); x <= building.x2(); x++) {
            for (int z = building.z1(); z <= building.z2(); z++) {
                highest = Math.max(highest, world.getHighestBlockYAt(x, z,
                        org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES));
            }
        }
        if (building.hasHeight()) highest = Math.min(highest, building.yMax());
        return highest + 1;
    }

    /** Contour horizontal de la zone à la hauteur y. */
    private void rectangle(World world, double minX, double maxX, double minZ, double maxZ, double y, double step) {
        for (double x = minX; x <= maxX; x += step) {
            dot(world, x, y, minZ);
            dot(world, x, y, maxZ);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            dot(world, minX, y, z);
            dot(world, maxX, y, z);
        }
    }

    /** Une particule, visible du seul joueur concerné. */
    private void dot(World world, double x, double y, double z) {
        player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, 0, 0, 0, 0, DUST);
    }
}
