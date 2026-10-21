package com.citycore.raid;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Arrays;

/**
 * Camp des joueurs (PLAN_RAIDS §2.8) : le point de départ du raid, à côté de la structure, et le
 * point de réapparition des joueurs morts. Posé à {@code distance} blocs du bord de la boîte de la
 * cible (hors de portée des archers postés aux murs), du côté le plus plat, face à la cible : un
 * plancher de 5 × 5 à hauteur du sol (sur l'eau : un radeau), un feu de camp et une bannière de
 * Valmont. Le monde des raids est jetable : le camp disparaît avec lui.
 *
 * Les chunks doivent être chargés (le site est généré avec une marge qui couvre le camp).
 */
final class RaidCamp {

    /**
     * Camp posé.
     *
     * @param spawn  point de réapparition (au centre du plancher, face à la cible)
     * @param herald place du héraut (au bord du plancher)
     * @param water  radeau sur l'eau
     */
    record Camp(Location spawn, Location herald, boolean water) {}

    /** Demi-côté du plancher (5 × 5). */
    private static final int HALF = 2;

    private RaidCamp() {}

    /** Choisit le côté et pose le camp ; null si aucun côté n'est dans des chunks chargés. */
    static Camp build(World w, BoundingBox box, int distance) {
        double cx = box.getCenterX(), cz = box.getCenterZ();
        double hx = box.getWidthX() / 2 + distance, hz = box.getWidthZ() / 2 + distance;
        // Côtés, puis coins : en diagonale depuis un coin de la boîte, à la même distance environ
        // (0,7 × distance sur chaque axe), pour rester hors de la cible et dans la marge générée
        double dx = box.getWidthX() / 2 + distance * 0.7, dz = box.getWidthZ() / 2 + distance * 0.7;
        double[][] candidates = {
                {cx, cz + hz}, {cx, cz - hz}, {cx + hx, cz}, {cx - hx, cz},
                {cx + dx, cz + dz}, {cx - dx, cz + dz}, {cx + dx, cz - dz}, {cx - dx, cz - dz}};
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (double[] c : candidates) {
            int x = (int) Math.floor(c[0]), z = (int) Math.floor(c[1]);
            int[] eval = evaluate(w, x, z);
            if (eval == null) continue;
            // Relief d'abord ; l'eau compte beaucoup (un camp sur la terre ferme vaut mieux)
            double score = eval[0] * 2.0 + eval[1] * 10.0;
            if (score < bestScore) {
                bestScore = score;
                best = new int[]{x, z, eval[2], eval[1]};
            }
        }
        if (best == null) return null;
        int x = best[0], z = best[1], floorY = best[2];
        boolean water = best[3] > (2 * HALF + 1) * (2 * HALF + 1) / 2;
        lay(w, x, z, floorY, water);

        Location spawn = new Location(w, x + 0.5, floorY + 1, z + 0.5);
        Vector toTarget = new Vector(cx - spawn.getX(), 0, cz - spawn.getZ());
        if (toTarget.lengthSquared() > 1e-6) spawn.setDirection(toTarget);
        spawn.setPitch(0f);
        Location herald = new Location(w, x + HALF + 0.5, floorY + 1, z + 0.5);
        herald.setDirection(spawn.toVector().subtract(herald.toVector()));
        herald.setPitch(0f);
        return new Camp(spawn, herald, water);
    }

    /**
     * Relief (écart de hauteur), nombre de colonnes d'eau et hauteur du plancher sur 5 × 5 autour
     * de (x, z) ; null si un chunk n'est pas chargé.
     */
    private static int[] evaluate(World w, int x, int z) {
        int[] heights = new int[(2 * HALF + 1) * (2 * HALF + 1)];
        int i = 0, water = 0;
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                if (!w.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return null;
                int y = w.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (w.getBlockAt(x + dx, y, z + dz).isLiquid()) water++;
                heights[i++] = y;
            }
        }
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        int relief = sorted[sorted.length - 1] - sorted[0];
        int median = sorted[sorted.length / 2];
        return new int[]{relief, water, median};
    }

    /** Plancher (ou radeau) de 5 × 5 à {@code floorY}, dégagé au-dessus, feu de camp et bannière. */
    private static void lay(World w, int x, int z, int floorY, boolean water) {
        Material deck = water ? Material.OAK_PLANKS : Material.SPRUCE_PLANKS;
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                int bx = x + dx, bz = z + dz;
                w.getBlockAt(bx, floorY, bz).setType(deck, false);
                for (int dy = 1; dy <= 3; dy++) {
                    Block above = w.getBlockAt(bx, floorY + dy, bz);
                    if (!above.getType().isAir()) above.setType(Material.AIR, false);
                }
                if (!water) {
                    // Trou sous le plancher (pente) : comblé de terre sur quelques blocs
                    for (int dy = 1; dy <= 4; dy++) {
                        Block below = w.getBlockAt(bx, floorY - dy, bz);
                        if (below.getType().isSolid()) break;
                        below.setType(Material.DIRT, false);
                    }
                }
            }
        }
        w.getBlockAt(x - HALF, floorY + 1, z + HALF).setType(Material.CAMPFIRE, false);
        w.getBlockAt(x - HALF, floorY + 1, z - HALF).setType(Material.YELLOW_BANNER, false); // or de Valmont
    }
}
