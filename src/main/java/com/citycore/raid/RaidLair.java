package com.citycore.raid;

import com.citycore.util.GroundFinder;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Repaires enfouis (PLAN_RAIDS §5.7, phase R8) : catacombes, temple enfoui, monument souterrain,
 * salle du Wroughtnaut. Le camp reste en surface ; le plugin creuse depuis le camp un puits à
 * échelles puis une galerie jusqu'à la première salle repérée dans la structure. Parois, sol et
 * plafond qui manquent (grotte traversée, eau, lave) sont comblés de pierre ; la galerie est
 * éclairée de lumières invisibles.
 *
 * Les chunks doivent être chargés (site généré avec sa marge).
 */
final class RaidLair {

    private RaidLair() {}

    /**
     * Première salle : une place où se tenir dans la structure, à couvert, près d'une porte, d'un
     * coffre, d'une cage ou d'une cloche ; sinon la plus proche du centre ; null s'il n'y en a pas.
     */
    static Location room(Arena a) {
        World w = a.world();
        BoundingBox box = a.box();
        List<Location> anchors = new ArrayList<>(a.doors());
        anchors.addAll(a.containers());
        anchors.addAll(a.spawners());
        anchors.addAll(a.bells());
        for (Location anchor : anchors) {
            for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                Location at = GroundFinder.standAt(w, anchor.getBlockX() + d[0], anchor.getBlockY(), anchor.getBlockZ() + d[1]);
                if (at != null && covered(w, at) && box.contains(at.toVector())) return at;
            }
        }
        // Balayage de la boîte, du centre vers les bords
        Location c = a.center();
        int maxRing = (int) Math.max(box.getWidthX(), box.getWidthZ()) / 2;
        for (int ring = 0; ring <= maxRing; ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                for (int dz = -ring; dz <= ring; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = c.getBlockX() + dx, z = c.getBlockZ() + dz;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Location at = insideAt(w, x, z, box);
                    if (at != null) return at;
                }
            }
        }
        return null;
    }

    /**
     * Une place où se tenir dans la colonne, à couvert (sous terre), en partant du bas de la
     * boîte ; null s'il n'y en a pas.
     */
    static Location insideAt(World w, int x, int z, BoundingBox box) {
        int from = (int) Math.floor(box.getMinY()) + 1, to = (int) Math.ceil(box.getMaxY()) - 1;
        for (int y = from; y < to; y++) {
            Block below = w.getBlockAt(x, y - 1, z), feet = w.getBlockAt(x, y, z), head = w.getBlockAt(x, y + 1, z);
            if (below.isLiquid() || !below.getType().isSolid()) continue;
            if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) continue;
            Location at = new Location(w, x + 0.5, y, z + 0.5);
            if (covered(w, at)) return at;
        }
        return null;
    }

    /** Sous terre : le sol de la surface est au moins 3 blocs plus haut. */
    private static boolean covered(World w, Location at) {
        return w.getHighestBlockYAt(at.getBlockX(), at.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) > at.getBlockY() + 2;
    }

    /**
     * Puits à échelles au bord du camp, du côté de la cible, puis galerie de 2 de haut jusqu'à la
     * salle. Renvoie la salle (cœur du repaire), ou null si aucune salle n'a été trouvée.
     */
    static Location dig(World w, Arena a, RaidCamp.Camp camp) {
        Location room = room(a);
        if (room == null) return null;
        Location spawn = camp.spawn();
        double dx = room.getX() - spawn.getX(), dz = room.getZ() - spawn.getZ();
        double len = Math.max(1e-3, Math.hypot(dx, dz));
        // Départ juste au-delà du camp (un camp de mod fait 12 × 12), vers la cible
        int sx = (int) Math.floor(spawn.getX() + dx / len * 8), sz = (int) Math.floor(spawn.getZ() + dz / len * 8);
        int top = w.getHighestBlockYAt(sx, sz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int bottom = room.getBlockY();
        int rx = room.getBlockX(), rz = room.getBlockZ();
        // Premier pas de la galerie : l'échelle s'accroche à la paroi opposée
        BlockFace first = Math.abs(rx - sx) >= Math.abs(rz - sz)
                ? (rx >= sx ? BlockFace.EAST : BlockFace.WEST) : (rz >= sz ? BlockFace.SOUTH : BlockFace.NORTH);
        if (top > bottom) {
            for (int y = top; y >= bottom; y--) {
                Block cell = w.getBlockAt(sx, y, sz);
                for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                    if (y == bottom && f == first) continue; // ouverture de la galerie
                    Block side = cell.getRelative(f);
                    if (!side.getType().isSolid() || side.isLiquid()) side.setType(Material.COBBLESTONE, false);
                }
                cell.setType(Material.LADDER, false);
                if (cell.getBlockData() instanceof Directional d) {
                    d.setFacing(first);
                    cell.setBlockData(d, false);
                }
            }
            // Repère à l'entrée : poteau à lanterne
            Block post = w.getBlockAt(sx, top + 1, sz).getRelative(first.getOppositeFace()).getRelative(BlockFace.DOWN);
            Block postTop = post.getRelative(BlockFace.UP);
            if (postTop.getType().isAir() || postTop.isPassable()) {
                postTop.setType(Material.SPRUCE_FENCE, false);
                postTop.getRelative(BlockFace.UP).setType(Material.LANTERN, false);
            }
            Block bottomFloor = w.getBlockAt(sx, bottom - 1, sz);
            if (!bottomFloor.getType().isSolid() || bottomFloor.isLiquid()) bottomFloor.setType(Material.COBBLESTONE, false);
        }
        // Galerie : un pas à la fois sur l'axe le plus en retard, à la hauteur de la salle
        int x = sx, z = sz, y = bottom, steps = 0;
        while ((x != rx || z != rz) && steps < 400) {
            if (Math.abs(rx - x) >= Math.abs(rz - z)) x += Integer.signum(rx - x);
            else z += Integer.signum(rz - z);
            steps++;
            carve(w, x, y, z, steps % 6 == 0);
        }
        return room;
    }

    /**
     * Cible flottante (château dans le ciel) : tour d'échelles contre un pilier de pierre, du sol
     * (ou de l'eau) jusqu'au bord de la structure le plus proche du camp. Renvoie le palier
     * d'arrivée sur la structure, ou null.
     */
    static Location climb(World w, Arena a, RaidCamp.Camp camp) {
        BoundingBox box = a.box();
        Location spawn = camp.spawn();
        // Bord de la boîte le plus proche du camp : on longe ce bord pour trouver où prendre pied
        int ex = (int) Math.floor(Math.max(box.getMinX(), Math.min(box.getMaxX() - 1, spawn.getX())));
        int ez = (int) Math.floor(Math.max(box.getMinZ(), Math.min(box.getMaxZ() - 1, spawn.getZ())));
        Location landing = null;
        for (int k = 0; k < 24 && landing == null; k++) {
            for (int[] d : new int[][]{{k, 0}, {-k, 0}, {0, k}, {0, -k}}) {
                int x = ex + d[0], z = ez + d[1];
                if (!box.contains(x + 0.5, box.getCenterY(), z + 0.5) || !w.isChunkLoaded(x >> 4, z >> 4)) continue;
                Location at = GroundFinder.groundAt(w, x, z);
                if (at != null && at.getY() > box.getMinY()) { landing = at; break; }
            }
        }
        if (landing == null) return null;
        // La tour se dresse juste hors de la boîte, face au palier ; le pilier est derrière les échelles
        int lx = landing.getBlockX(), lz = landing.getBlockZ(), ly = landing.getBlockY();
        BlockFace toward;
        if (lx == (int) Math.floor(box.getMinX())) toward = BlockFace.EAST;
        else if (lx == (int) Math.floor(box.getMaxX() - 1)) toward = BlockFace.WEST;
        else toward = lz <= box.getCenterZ() ? BlockFace.SOUTH : BlockFace.NORTH;
        int tx = lx - toward.getModX(), tz = lz - toward.getModZ();
        int px = tx - toward.getModX(), pz = tz - toward.getModZ();
        int ground = w.getHighestBlockYAt(tx, tz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (ground >= ly) return landing; // déjà accessible à pied
        for (int y = ground; y <= ly; y++) {
            w.getBlockAt(px, y, pz).setType(Material.COBBLESTONE, false);
            Block cell = w.getBlockAt(tx, y, tz);
            cell.setType(Material.LADDER, false);
            if (cell.getBlockData() instanceof Directional d) {
                d.setFacing(toward);
                cell.setBlockData(d, false);
            }
        }
        w.getBlockAt(px, ly + 1, pz).setType(Material.LANTERN, false); // repère en haut de la tour
        return landing;
    }

    /** Une case de galerie : pieds et tête dégagés, sol, plafond et parois comblés au besoin. */
    private static void carve(World w, int x, int y, int z, boolean light) {
        Block feet = w.getBlockAt(x, y, z), head = feet.getRelative(BlockFace.UP);
        Block floor = feet.getRelative(BlockFace.DOWN), ceiling = head.getRelative(BlockFace.UP);
        if (!floor.getType().isSolid() || floor.isLiquid()) floor.setType(Material.COBBLESTONE, false);
        if (ceiling.isLiquid() || ceiling.getType() == Material.SAND || ceiling.getType() == Material.GRAVEL
                || ceiling.getType() == Material.RED_SAND) ceiling.setType(Material.COBBLESTONE, false);
        for (Block b : new Block[]{feet, head}) {
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block side = b.getRelative(f);
                if (side.isLiquid()) side.setType(Material.COBBLESTONE, false);
            }
        }
        if (!feet.isPassable() || feet.isLiquid()) feet.setType(Material.AIR, false);
        if (light) head.setType(Material.LIGHT, false);
        else if (!head.isPassable() || head.isLiquid()) head.setType(Material.AIR, false);
    }
}
