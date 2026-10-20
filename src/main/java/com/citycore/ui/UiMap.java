package com.citycore.ui;

import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Carte vue du ciel d'une zone du monde, pour les écrans « Valmont » : une couleur par case,
 * prise au bloc le plus haut (couleurs des cartes du jeu), ombrée selon le relief comme une carte
 * en papier (plus clair en montant vers le sud, plus sombre en descendant ; eau plus sombre en
 * profondeur).
 *
 * Seuls les chunks déjà chargés sont lus, jamais chargés pour l'occasion : ailleurs la case vaut
 * 0 (inconnue) et laisse voir le parchemin du fond.
 */
public final class UiMap {

    /** Zone cartographiée : case (0, 0) en haut à gauche (nord-ouest), {@code scale} blocs par case. */
    public record Area(World world, int originX, int originZ, int scale, int cols, int rows) {

        /** Zone de cols x rows cases centrée sur (x, z). */
        public static Area centered(World world, double x, double z, int scale, int cols, int rows) {
            return new Area(world, (int) Math.floor(x) - cols * scale / 2, (int) Math.floor(z) - rows * scale / 2,
                    scale, cols, rows);
        }

        /** Colonne de la case qui contient l'abscisse x (peut sortir de la carte). */
        public int col(double x) {
            return Math.floorDiv((int) Math.floor(x) - originX, scale);
        }

        public int row(double z) {
            return Math.floorDiv((int) Math.floor(z) - originZ, scale);
        }

        public boolean inside(int col, int row) {
            return col >= 0 && row >= 0 && col < cols && row < rows;
        }

        /** Bloc lu pour la case (son centre). */
        public int blockX(int col) { return originX + col * scale + scale / 2; }

        public int blockZ(int row) { return originZ + row * scale + scale / 2; }
    }

    private UiMap() {}

    /** Couleurs du terrain (RGB, 0 = inconnu), rangée par rangée : {@code colors[row][col]}. */
    public static int[][] terrain(Area a) {
        int[][] colors = new int[a.rows()][a.cols()];
        // Hauteur de la rangée au nord de la carte, pour ombrer la première rangée
        int[] north = new int[a.cols()];
        for (int c = 0; c < a.cols(); c++) north[c] = height(a.world(), a.blockX(c), a.blockZ(0) - a.scale());
        for (int r = 0; r < a.rows(); r++) {
            for (int c = 0; c < a.cols(); c++) {
                int x = a.blockX(c), z = a.blockZ(r);
                if (!a.world().isChunkLoaded(x >> 4, z >> 4)) {
                    north[c] = Integer.MIN_VALUE;
                    continue;
                }
                int y = a.world().getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                Block block = surface(a.world(), x, y, z);
                int rgb = rgb(block);
                if (rgb == 0) {
                    north[c] = y;
                    continue;
                }
                double shade;
                if (block.getType() == Material.WATER) {
                    // Eau : plus sombre en profondeur
                    int depth = 0;
                    Block b = block;
                    while (depth < 10 && b.getType() == Material.WATER && b.getY() > a.world().getMinHeight()) {
                        depth++;
                        b = b.getRelative(0, -1, 0);
                    }
                    shade = depth < 3 ? 1.0 : depth < 6 ? 0.86 : 0.71;
                } else {
                    int up = north[c] == Integer.MIN_VALUE ? y : north[c];
                    shade = y > up ? 1.0 : y == up ? 0.86 : 0.71;
                }
                colors[r][c] = scale(rgb, shade);
                north[c] = block.getY();
            }
        }
        return colors;
    }

    /** Hauteur de la surface en (x, z), ou MIN_VALUE si le chunk n'est pas chargé. */
    private static int height(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return Integer.MIN_VALUE;
        return world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
    }

    /** Premier bloc visible sur une carte en descendant depuis y (verre, air… sautés). */
    private static Block surface(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        for (int k = 0; k < 8 && rgb(block) == 0 && block.getY() > world.getMinHeight(); k++) {
            block = block.getRelative(0, -1, 0);
        }
        return block;
    }

    /** Couleur de carte d'un bloc (0 = invisible sur une carte). */
    private static int rgb(Block block) {
        Color c = block.getBlockData().getMapColor();
        return c == null ? 0 : c.asRGB();
    }

    private static int scale(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return Math.max(1, (r << 16) | (g << 8) | b); // jamais 0 : 0 veut dire « inconnu »
    }
}
