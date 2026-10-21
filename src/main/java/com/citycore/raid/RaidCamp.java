package com.citycore.raid;

import com.citycore.util.GroundFinder;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.loot.Lootable;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Camp des joueurs (PLAN_RAIDS §2.8) : le point de départ du raid, à côté de la structure, et le
 * point de réapparition des joueurs morts. Posé à {@code distance} blocs du bord de la boîte de la
 * cible (hors de portée des archers postés aux murs), du côté le plus plat, face à la cible.
 *
 * Un camp d'un mod d'abord (raids.camp.modeles : camp de bandits, camp de l'Égide de Valarian
 * Conquest…) : son gabarit est posé tel quel, sans ses occupants, coffres et tonneaux vidés, sur
 * un sol comblé. Sur un terrain trop accidenté ou sans modèle : le camp du plugin, un plancher de
 * 7 × 7 avec feu de camp, lanternes et bannière de Valmont. Sur l'eau (abordages, R7) : le radeau
 * de Valmont, 9 × 9 avec bastingage, mât et bannière, et des places d'amarrage pour les barques.
 * Le monde des raids est jetable : le camp disparaît avec lui.
 *
 * Les chunks doivent être chargés (le site est généré avec une marge qui couvre le camp).
 */
final class RaidCamp {

    /**
     * Camp posé.
     *
     * @param spawn  point de réapparition (au milieu du camp, face à la cible)
     * @param herald place du héraut
     * @param water    radeau sur l'eau
     * @param model    gabarit posé, ou null (camp du plugin)
     * @param moorings places des barques autour du radeau (vide sur la terre ferme)
     */
    record Camp(Location spawn, Location herald, boolean water, String model, List<Location> moorings) {}

    /** Étiquette des barques du radeau (retirées au nettoyage, indestructibles). */
    static final String TAG_CAMP = "citycore.raid.camp";

    /** Gabarit de camp d'un mod, chargé au lancement de l'expédition. */
    record Model(String id, Structure structure) {
        int width()  { return structure.getSize().getBlockX(); }
        int height() { return structure.getSize().getBlockY(); }
        int depth()  { return structure.getSize().getBlockZ(); }
        /** Demi-côté de son emprise au sol (pour la marge générée autour de la cible). */
        int reach()  { return Math.max(width(), depth()) / 2 + 1; }
    }

    /** Demi-côté du plancher du camp du plugin (7 × 7) et du radeau (9 × 9). */
    private static final int HALF = 3;
    private static final int RAFT = 4;
    /** Relief au-delà duquel un gabarit n'est pas posé (il flotterait ou s'enterrerait). */
    private static final int MAX_RELIEF = 6;

    private RaidCamp() {}

    /**
     * Un modèle au hasard parmi ceux que le serveur connaît (gabarits des mods installés), ou null.
     */
    static Model pick(List<String> ids, Logger log) {
        List<String> order = new ArrayList<>(ids);
        Collections.shuffle(order);
        for (String id : order) {
            NamespacedKey key = NamespacedKey.fromString(id.trim().toLowerCase(Locale.ROOT));
            if (key == null) continue;
            try {
                Structure s = Bukkit.getStructureManager().loadStructure(key, false);
                if (s != null && s.getSize().getBlockX() > 0 && s.getSize().getBlockZ() > 0) return new Model(id, s);
            } catch (RuntimeException e) {
                log.fine("⚔ [Raids] Modèle de camp " + id + " illisible : " + e.getMessage());
            }
        }
        return null;
    }

    /** Choisit le côté et pose le camp ; null si aucun côté n'est dans des chunks chargés. */
    static Camp build(World w, BoundingBox box, int distance, Model model) {
        if (w.getEnvironment() != World.Environment.NORMAL) return buildOther(w, box, distance);
        double cx = box.getCenterX(), cz = box.getCenterZ();
        int reach = model != null ? model.reach() : HALF;
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
            int[] eval = evaluate(w, x, z, reach);
            if (eval == null) continue;
            // Relief d'abord ; l'eau compte beaucoup (un camp sur la terre ferme vaut mieux)
            double score = eval[0] * 2.0 + eval[1] * 10.0;
            if (score < bestScore) {
                bestScore = score;
                best = new int[]{x, z, eval[2], eval[1], eval[0]};
            }
        }
        if (best == null) return null;
        int x = best[0], z = best[1], floorY = best[2], water = best[3], relief = best[4];
        if (model != null && water == 0 && relief <= MAX_RELIEF) {
            Camp c = placeModel(w, x, z, floorY, model, cx, cz);
            if (c != null) return c;
        }
        int side = (2 * reach + 1) * (2 * reach + 1);
        boolean raft = water > side / 2;
        List<Location> moorings = List.of();
        if (raft) moorings = layRaft(w, x, z, floorY);
        else lay(w, x, z, floorY);
        Location spawn = face(new Location(w, x + 0.5, floorY + 1, z + 0.5), cx, cz);
        Location herald = new Location(w, x + (raft ? 2 : HALF) + 0.5, floorY + 1, z + 0.5);
        herald.setDirection(spawn.toVector().subtract(herald.toVector()));
        herald.setPitch(0f);
        return new Camp(spawn, herald, raft, null, moorings);
    }

    /**
     * Relief (écart de hauteur), nombre de colonnes d'eau et hauteur du sol (médiane) sur le carré
     * de demi-côté {@code half} autour de (x, z) ; null si un chunk n'est pas chargé.
     */
    static int[] evaluate(World w, int x, int z, int half) {
        int[] heights = new int[(2 * half + 1) * (2 * half + 1)];
        int i = 0, water = 0;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
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

    /* =========================
       CAMP D'UN MOD (gabarit)
       ========================= */

    /**
     * Pose le gabarit centré sur (x, z), sa couche du bas à la hauteur du sol, tourné au hasard ;
     * sans ses occupants (bandits, gardes), coffres et tonneaux vidés, sol comblé dessous.
     */
    private static Camp placeModel(World w, int x, int z, int floorY, Model model, double cx, double cz) {
        BoundingBox placed = RaidTemplates.place(w, model.structure(), x, z, floorY, RaidTemplates.randomRotation());
        if (placed == null) return null;
        int fw = (int) placed.getWidthX(), fd = (int) placed.getWidthZ();
        // Point de départ : une place libre au plus près du milieu (pas sur le feu de camp)
        Location spawn = spot(w, x, floorY + 1, z, 0, Math.max(fw, fd) / 2, null);
        if (spawn == null) spawn = new Location(w, x + 0.5, floorY + 1, z + 0.5);
        face(spawn, cx, cz);
        Location herald = spot(w, spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ(), 2, 5, spawn);
        if (herald == null) herald = spawn.clone().add(1, 0, 0);
        herald.setDirection(spawn.toVector().subtract(herald.toVector()));
        herald.setPitch(0f);
        // Bannière de Valmont à côté du héraut
        Location banner = spot(w, herald.getBlockX(), herald.getBlockY(), herald.getBlockZ(), 1, 2, spawn);
        if (banner != null && banner.distanceSquared(herald) > 0.5 && banner.getBlock().getType().isAir()) {
            banner.getBlock().setType(Material.YELLOW_BANNER, false);
        }
        return new Camp(spawn, herald, false, model.id(), List.of());
    }

    /**
     * Place où se tenir à environ {@code y}, entre {@code from} et {@code to} blocs de (x, z), en
     * partant du plus près ; ni sur un feu, ni sur {@code avoid}. Null s'il n'y en a pas.
     */
    private static Location spot(World w, int x, int y, int z, int from, int to, Location avoid) {
        for (int r = from; r <= to; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // anneau de rayon r
                    Location at = GroundFinder.standAt(w, x + dx, y, z + dz);
                    if (at == null) continue;
                    Material below = at.clone().add(0, -1, 0).getBlock().getType();
                    if (below == Material.CAMPFIRE || below == Material.SOUL_CAMPFIRE || below == Material.MAGMA_BLOCK
                            || below.name().endsWith("_BED")) continue;
                    if (avoid != null && at.getBlockX() == avoid.getBlockX() && at.getBlockZ() == avoid.getBlockZ()) continue;
                    return at;
                }
            }
        }
        return null;
    }

    private static Location face(Location at, double cx, double cz) {
        Vector toTarget = new Vector(cx - at.getX(), 0, cz - at.getZ());
        if (toTarget.lengthSquared() > 1e-6) at.setDirection(toTarget);
        at.setPitch(0f);
        return at;
    }

    /* =========================
       NETHER ET END (R9) : plateforme près de la base de la cible
       ========================= */

    /**
     * Nether ou End : le plus haut bloc d'une colonne est le toit de bedrock (Nether) ou n'existe
     * pas (vide de l'End). Les places sont cherchées près de la base de la cible, sur des anneaux
     * de 6 à 34 blocs autour de sa boîte : sol ferme, peu de relief, ni lave ni vide. La meilleure
     * reçoit une plateforme de 7 × 7 (basalte et pierre noire dans le Nether, obsidienne dans
     * l'End) ; s'il n'y a que du vide ou de la lave, la plateforme est posée au plus près, à la
     * hauteur de la base, avec une passerelle jusqu'à la cible.
     */
    private static Camp buildOther(World w, BoundingBox box, int distance) {
        boolean nether = w.getEnvironment() == World.Environment.NETHER;
        double cx = box.getCenterX(), cz = box.getCenterZ();
        int base = (int) Math.floor(box.getMinY()) + 1;
        int[] rings = {6, 12, 18, 26, Math.max(30, Math.min(distance, 34))};
        double[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {0.7, 0.7}, {-0.7, 0.7}, {0.7, -0.7}, {-0.7, -0.7}};
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        int[] nearest = null;
        for (int ring : rings) {
            for (double[] d : dirs) {
                int x = (int) Math.floor(cx + d[0] * (box.getWidthX() / 2 + ring));
                int z = (int) Math.floor(cz + d[1] * (box.getWidthZ() / 2 + ring));
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                if (nearest == null) nearest = new int[]{x, z, (int) Math.signum(cx - x), (int) Math.signum(cz - z)};
                int[] eval = evaluateFloor(w, x, z, base, nether);
                if (eval == null) continue;
                double score = eval[1] * 10.0 + eval[0] * 2.0 + ring * 0.2; // trous, relief, distance
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{x, z, eval[2], eval[1], eval[0]};
                }
            }
        }
        if (best != null && best[3] <= 6 && best[4] <= 4) {
            layOther(w, best[0], best[1], best[2], nether);
            return otherCamp(w, best[0], best[1], best[2], cx, cz);
        }
        if (nearest == null) return null;
        // Que du vide ou de la lave : plateforme à la hauteur de la base, passerelle vers la cible
        int x = nearest[0], z = nearest[1], y = base - 1;
        layOther(w, x, z, y, nether);
        bridge(w, x, z, y, cx, cz, box, nether);
        return otherCamp(w, x, z, y, cx, cz);
    }

    private static Camp otherCamp(World w, int x, int z, int y, double cx, double cz) {
        Location spawn = face(new Location(w, x + 0.5, y + 1, z + 0.5), cx, cz);
        Location herald = new Location(w, x + HALF - 1 + 0.5, y + 1, z + 0.5);
        herald.setDirection(spawn.toVector().subtract(herald.toVector()));
        herald.setPitch(0f);
        return new Camp(spawn, herald, false, null, List.of());
    }

    /**
     * Sol près de {@code base} sur 7 × 7 : relief, colonnes sans sol (vide, lave, eau), hauteur
     * médiane du sol ; null si toutes les colonnes manquent.
     */
    private static int[] evaluateFloor(World w, int x, int z, int base, boolean nether) {
        List<Integer> heights = new ArrayList<>();
        int missing = 0;
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                Integer y = floorNear(w, x + dx, z + dz, base, nether);
                if (y == null) missing++;
                else heights.add(y);
            }
        }
        if (heights.isEmpty()) return null;
        Collections.sort(heights);
        int relief = heights.get(heights.size() - 1) - heights.get(0);
        return new int[]{relief, missing, heights.get(heights.size() / 2)};
    }

    /** Sol ferme (deux blocs libres au-dessus, ni lave ni eau) le plus près de {@code base}, de +16 à -16. */
    private static Integer floorNear(World w, int x, int z, int base, boolean nether) {
        int roof = nether ? 120 : w.getMaxHeight() - 2;
        for (int k = 0; k <= 16; k++) {
            for (int y : new int[]{base + k, base - k}) {
                if (y <= w.getMinHeight() + 1 || y >= roof) continue;
                Block floor = w.getBlockAt(x, y, z);
                if (!floor.getType().isSolid() || floor.isLiquid()) continue;
                Block feet = floor.getRelative(org.bukkit.block.BlockFace.UP), head = feet.getRelative(org.bukkit.block.BlockFace.UP);
                if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) continue;
                return y;
            }
        }
        return null;
    }

    /** Plateforme de 7 × 7 : dalle, bordure, lanternes, feu de camp, bannière de Valmont. */
    private static void layOther(World w, int x, int z, int y, boolean nether) {
        Material deck = nether ? Material.POLISHED_BLACKSTONE_BRICKS : Material.OBSIDIAN;
        Material edge = nether ? Material.POLISHED_BASALT : Material.END_STONE_BRICKS;
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                boolean border = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                w.getBlockAt(x + dx, y, z + dz).setType(border ? edge : deck, false);
                for (int dy = 1; dy <= 4; dy++) {
                    Block above = w.getBlockAt(x + dx, y + dy, z + dz);
                    if (!above.getType().isAir()) above.setType(Material.AIR, false);
                }
            }
        }
        for (int[] c : new int[][]{{-HALF, -HALF}, {-HALF, HALF}, {HALF, -HALF}, {HALF, HALF}}) {
            w.getBlockAt(x + c[0], y + 1, z + c[1]).setType(nether ? Material.BLACKSTONE_WALL : Material.END_ROD, false);
            if (nether) w.getBlockAt(x + c[0], y + 2, z + c[1]).setType(Material.SOUL_LANTERN, false);
        }
        w.getBlockAt(x - 2, y + 1, z + 2).setType(nether ? Material.SOUL_CAMPFIRE : Material.CAMPFIRE, false);
        w.getBlockAt(x - 2, y + 1, z - 2).setType(Material.YELLOW_BANNER, false); // or de Valmont
    }

    /**
     * Passerelle de 3 de large, à la hauteur de la plateforme, jusqu'à la boîte de la cible (au plus
     * 48 blocs) : comble le vide et la lave, dégage la tête.
     */
    private static void bridge(World w, int x, int z, int y, double cx, double cz, BoundingBox box, boolean nether) {
        Material deck = nether ? Material.POLISHED_BLACKSTONE_BRICKS : Material.OBSIDIAN;
        double dx = cx - x, dz = cz - z, len = Math.max(1e-3, Math.hypot(dx, dz));
        double ux = dx / len, uz = dz / len;
        for (int step = HALF; step < 48; step++) {
            int bx = (int) Math.floor(x + 0.5 + ux * step), bz = (int) Math.floor(z + 0.5 + uz * step);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return;
            if (box.contains(bx + 0.5, y + 1, bz + 0.5)) return; // arrivé à la cible
            for (int side = -1; side <= 1; side++) {
                int sx = bx + (int) Math.round(-uz * side), sz = bz + (int) Math.round(ux * side);
                Block floor = w.getBlockAt(sx, y, sz);
                if (!floor.getType().isSolid() || floor.isLiquid()) floor.setType(deck, false);
                for (int dy = 1; dy <= 2; dy++) {
                    Block above = w.getBlockAt(sx, y + dy, sz);
                    if (above.isLiquid() || (!above.isPassable() && !box.contains(sx + 0.5, y + dy, sz + 0.5))) above.setType(Material.AIR, false);
                }
            }
        }
    }

    /* =========================
       CAMP DU PLUGIN (plancher ou radeau)
       ========================= */

    /**
     * Radeau de Valmont (9 × 9) sur l'eau à {@code y} : pont de chêne bordé de rondins, bastingage
     * ouvert au milieu de chaque côté (accès des barques), lanternes aux coins, mât et bannière de
     * Valmont, feu de camp, foin, tonneau (vide). Renvoie les places d'amarrage, sur l'eau autour.
     */
    private static List<Location> layRaft(World w, int x, int z, int y) {
        for (int dx = -RAFT; dx <= RAFT; dx++) {
            for (int dz = -RAFT; dz <= RAFT; dz++) {
                int bx = x + dx, bz = z + dz;
                boolean edge = Math.abs(dx) == RAFT || Math.abs(dz) == RAFT;
                boolean opening = (Math.abs(dx) <= 1 && Math.abs(dz) == RAFT) || (Math.abs(dz) <= 1 && Math.abs(dx) == RAFT);
                w.getBlockAt(bx, y, bz).setType(edge ? Material.STRIPPED_SPRUCE_LOG : Material.OAK_PLANKS, false);
                for (int dy = 1; dy <= 6; dy++) {
                    Block above = w.getBlockAt(bx, y + dy, bz);
                    if (!above.getType().isAir()) above.setType(Material.AIR, false);
                }
                if (edge && !opening) w.getBlockAt(bx, y + 1, bz).setType(Material.SPRUCE_FENCE, false);
            }
        }
        for (int[] c : new int[][]{{-RAFT, -RAFT}, {-RAFT, RAFT}, {RAFT, -RAFT}, {RAFT, RAFT}}) {
            w.getBlockAt(x + c[0], y + 2, z + c[1]).setType(Material.LANTERN, false);
        }
        // Mât : rondins, vergue de clôtures, bannière de Valmont au sommet
        for (int dy = 1; dy <= 5; dy++) w.getBlockAt(x, y + dy, z + 2).setType(Material.SPRUCE_LOG, false);
        w.getBlockAt(x - 1, y + 4, z + 2).setType(Material.SPRUCE_FENCE, false);
        w.getBlockAt(x + 1, y + 4, z + 2).setType(Material.SPRUCE_FENCE, false);
        w.getBlockAt(x, y + 6, z + 2).setType(Material.YELLOW_BANNER, false);
        w.getBlockAt(x - 2, y + 1, z - 2).setType(Material.CAMPFIRE, false);
        w.getBlockAt(x + 2, y + 1, z - 2).setType(Material.BARREL, false);
        w.getBlockAt(x - 2, y + 1, z + 2).setType(Material.HAY_BLOCK, false);
        // Amarrage : devant chaque ouverture, puis de part et d'autre
        List<Location> moorings = new ArrayList<>();
        int out = RAFT + 2;
        int[][] spots = {{out, 0}, {-out, 0}, {0, out}, {0, -out}, {out, 3}, {-out, -3}, {3, out}, {-3, -out}, {out, -3}, {-out, 3}};
        for (int[] s : spots) {
            Block b = w.getBlockAt(x + s[0], y, z + s[1]);
            if (b.isLiquid()) moorings.add(b.getLocation().add(0.5, 1, 0.5));
        }
        return moorings;
    }

    /**
     * Plancher de 7 × 7 à {@code floorY}, dégagé au-dessus ; poteaux à lanterne aux coins, feu de
     * camp, botte de foin, tonneau (vide) et bannière de Valmont.
     */
    private static void lay(World w, int x, int z, int floorY) {
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                int bx = x + dx, bz = z + dz;
                boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                w.getBlockAt(bx, floorY, bz).setType(edge ? Material.STRIPPED_SPRUCE_LOG : Material.SPRUCE_PLANKS, false);
                for (int dy = 1; dy <= 4; dy++) {
                    Block above = w.getBlockAt(bx, floorY + dy, bz);
                    if (!above.getType().isAir()) above.setType(Material.AIR, false);
                }
                // Trou sous le plancher (pente) : comblé de terre sur quelques blocs
                for (int dy = 1; dy <= 6; dy++) {
                    Block below = w.getBlockAt(bx, floorY - dy, bz);
                    if (below.getType().isSolid()) break;
                    below.setType(Material.DIRT, false);
                }
            }
        }
        for (int[] c : new int[][]{{-HALF, -HALF}, {-HALF, HALF}, {HALF, -HALF}, {HALF, HALF}}) {
            w.getBlockAt(x + c[0], floorY + 1, z + c[1]).setType(Material.SPRUCE_FENCE, false);
            w.getBlockAt(x + c[0], floorY + 2, z + c[1]).setType(Material.LANTERN, false);
        }
        w.getBlockAt(x - 2, floorY + 1, z + 2).setType(Material.CAMPFIRE, false);
        w.getBlockAt(x + 2, floorY + 1, z + 2).setType(Material.HAY_BLOCK, false);
        w.getBlockAt(x + 2, floorY + 1, z - 2).setType(Material.BARREL, false);
        w.getBlockAt(x - 2, floorY + 1, z - 2).setType(Material.YELLOW_BANNER, false); // or de Valmont
    }
}
