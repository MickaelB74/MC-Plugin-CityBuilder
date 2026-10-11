package com.citycore.building;

import org.bukkit.HeightMap;
import org.bukkit.World;

/**
 * Hauteur d'un bâtiment (les zones sont dessinées en x/z seulement).
 *
 * Règle : la zone va de {@code profondeur} blocs sous le point de définition le plus bas (les
 * coins cliqués, au sol) à {@code hauteur} blocs au-dessus du plus haut (config.yml,
 * batiments.hauteur / batiments.profondeur : 100 et 40 par défaut). Le sous-sol compte donc
 * (caves, mines), mais les blocs de terrain n'entrent pas dans la variété (batiments.terrain).
 * Pour un bâtiment dont on ne connaît pas les points de définition, le sol autour de la zone
 * sert de référence.
 */
public final class BuildingHeights {

    private static int height = 100;
    private static int depth  = 40;

    private BuildingHeights() {}

    /** Réglages de config.yml (appelé au chargement des exigences des bâtiments). */
    public static void configure(int newHeight, int newDepth) {
        height = Math.max(1, newHeight);
        depth  = Math.max(0, newDepth);
    }

    public static int height() { return height; }
    public static int depth()  { return depth; }

    /** {y_min, y_max} : profondeur sous le point le plus bas, hauteur au-dessus du plus haut. */
    public static int[] range(World world, int refLow, int refHigh) {
        int low  = Math.max(world.getMinHeight(), Math.min(refLow, refHigh) - depth);
        int high = Math.min(world.getMaxHeight() - 1, Math.max(refLow, refHigh) + height);
        return new int[]{low, high};
    }

    /**
     * Sol autour d'une zone : le plus bas des blocs les plus hauts d'un anneau d'un bloc à
     * l'extérieur (feuilles ignorées). Sert de point de définition aux bâtiments anciens.
     * Charge les chunks au besoin : fil principal uniquement.
     */
    public static int groundLevel(World world, int x1, int z1, int x2, int z2) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        int ground = Integer.MAX_VALUE;
        for (int x = minX - 1; x <= maxX + 1; x++) {
            ground = Math.min(ground, top(world, x, minZ - 1));
            ground = Math.min(ground, top(world, x, maxZ + 1));
        }
        for (int z = minZ; z <= maxZ; z++) {
            ground = Math.min(ground, top(world, minX - 1, z));
            ground = Math.min(ground, top(world, maxX + 1, z));
        }
        return ground;
    }

    /** Y du bloc le plus haut (sol, mur, toit) d'une colonne, feuilles ignorées. */
    private static int top(World world, int x, int z) {
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }
}
