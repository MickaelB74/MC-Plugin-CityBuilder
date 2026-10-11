package com.citycore.building;

import org.bukkit.HeightMap;
import org.bukkit.World;

/**
 * Estimation de la hauteur d'un bâtiment (les zones sont dessinées en x/z seulement).
 *
 * Bas : le sol autour de la zone (bloc le plus haut d'un anneau d'un bloc à l'extérieur, au plus
 * bas), ou les coins cliqués s'ils sont plus bas. Haut : le bloc le plus haut de la zone (feuilles
 * ignorées : un arbre qui déborde ne compte pas), ou les coins cliqués s'ils sont plus hauts.
 * Les chunks lus sont chargés au besoin : à n'appeler que sur le fil principal.
 */
public final class BuildingHeights {

    /** Hauteur maximale prise en compte au-dessus du sol (tour, clocher…). */
    public static final int MAX_HEIGHT = 64;
    /** Hauteur donnée à une zone encore vide (rien au-dessus du sol). */
    private static final int EMPTY_HEIGHT = 6;

    private BuildingHeights() {}

    /** {y_min, y_max} d'une zone, en tenant compte des coins cliqués (null = inconnus). */
    public static int[] estimate(World world, int x1, int z1, int x2, int z2,
                                 Integer clickedLow, Integer clickedHigh) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        // Sol : anneau d'un bloc autour de la zone, au plus bas
        int ground = Integer.MAX_VALUE;
        for (int x = minX - 1; x <= maxX + 1; x++) {
            ground = Math.min(ground, top(world, x, minZ - 1));
            ground = Math.min(ground, top(world, x, maxZ + 1));
        }
        for (int z = minZ; z <= maxZ; z++) {
            ground = Math.min(ground, top(world, minX - 1, z));
            ground = Math.min(ground, top(world, maxX + 1, z));
        }
        int low = clickedLow != null ? Math.min(ground, clickedLow) : ground;

        // Toit : bloc le plus haut à l'intérieur de la zone
        int roof = low;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) roof = Math.max(roof, top(world, x, z));
        }
        if (clickedHigh != null) roof = Math.max(roof, clickedHigh);
        if (roof <= low) roof = low + EMPTY_HEIGHT;

        low = Math.max(world.getMinHeight(), low);
        int high = Math.min(Math.min(roof, low + MAX_HEIGHT), world.getMaxHeight() - 1);
        return new int[]{low, high};
    }

    /** Y du bloc le plus haut (sol, mur, toit) d'une colonne, feuilles ignorées. */
    private static int top(World world, int x, int z) {
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }
}
