package com.citycore.util;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Recherche du sol naturel : sous la canopée des arbres, jamais sur un tronc, un feuillage ou
 * dans l'eau.
 *
 * Le bloc « le plus haut » de Minecraft compte les feuilles : un PNJ placé dessus apparaissait
 * au sommet des arbres. On part de la hauteur sans feuilles, puis on descend en ignorant troncs,
 * champignons géants et plantes jusqu'au premier bloc plein.
 */
public final class GroundFinder {

    private GroundFinder() {}

    /**
     * Point où se tenir debout en (x, z), au centre du bloc ; null s'il n'y a pas de sol sûr
     * (eau, lave, tronc au-dessus du sol, plafond trop bas).
     */
    public static Location groundAt(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int min = world.getMinHeight();
        for (; y > min; y--) {
            Block floor = world.getBlockAt(x, y, z);
            if (floor.isLiquid()) return null;                        // lac, rivière, lave
            Material m = floor.getType();
            if (m.isAir() || isTree(m) || !m.isSolid()) continue;    // tronc, champignon, herbe, fleur…
            Block feet = floor.getRelative(BlockFace.UP);
            Block head = feet.getRelative(BlockFace.UP);
            if (standable(feet) && standable(head)) return new Location(world, x + 0.5, y + 1, z + 0.5);
            return null;                                              // sol plein mais coincé dessous
        }
        return null;
    }

    /** Bloc traversable à hauteur de corps (air, herbes hautes, fleurs, neige fine). */
    private static boolean standable(Block block) {
        return block.isPassable() && !block.isLiquid() && !isTree(block.getType());
    }

    private static boolean isTree(Material m) {
        return Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m)
                || m == Material.MUSHROOM_STEM || m == Material.BROWN_MUSHROOM_BLOCK
                || m == Material.RED_MUSHROOM_BLOCK || Tag.WART_BLOCKS.isTagged(m)
                || m == Material.BAMBOO || m == Material.CACTUS || m == Material.VINE;
    }
}
