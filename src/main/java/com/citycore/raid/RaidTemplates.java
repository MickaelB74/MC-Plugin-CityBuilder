package com.citycore.raid;

import org.bukkit.Bukkit;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gabarits des mods posés par le plugin (camps, tours et sièges des villes fortifiées) : un seul
 * fichier .nbt, posé par l'API des structures de Paper (pas de /place, pas d'assemblage jigsaw),
 * sans ses occupants. Après la pose : sol comblé dessous, coffres et tonneaux vidés (§6.3), blocs
 * de raccord « jigsaw » laissés par le gabarit remplacés (terre au sol, air au-dessus).
 */
final class RaidTemplates {

    private RaidTemplates() {}

    /** Gabarit connu du serveur (data/<espace>/structure/<nom>.nbt d'un mod installé), ou null. */
    static Structure load(String id) {
        NamespacedKey key = id == null ? null : NamespacedKey.fromString(id.trim().toLowerCase(Locale.ROOT));
        if (key == null) return null;
        try {
            Structure s = Bukkit.getStructureManager().loadStructure(key, false);
            return s != null && s.getSize().getBlockX() > 0 && s.getSize().getBlockZ() > 0 ? s : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Plus grand côté de l'emprise au sol d'un gabarit. */
    static int span(Structure s) {
        return Math.max(s.getSize().getBlockX(), s.getSize().getBlockZ());
    }

    /** Une rotation au hasard. */
    static StructureRotation randomRotation() {
        StructureRotation[] all = StructureRotation.values();
        return all[ThreadLocalRandom.current().nextInt(all.length)];
    }

    /**
     * Pose le gabarit centré sur (x, z), sa couche du bas à {@code floorY}, tourné de {@code rot}.
     *
     * @return sa boîte dans le monde, ou null si la pose a échoué
     */
    static BoundingBox place(World w, Structure s, int x, int z, int floorY, StructureRotation rot) {
        return place(w, s, x, z, floorY, rot, -1);
    }

    /**
     * Comme {@link #place}, pour un bateau quand {@code waterY} est la hauteur de la surface de
     * l'eau (sinon -1) : rien n'est comblé dessous, et l'air que le gabarit laisse sous la surface
     * hors de la coque redevient de l'eau (le bateau trempe dans l'eau, son intérieur reste sec).
     */
    static BoundingBox place(World w, Structure s, int x, int z, int floorY, StructureRotation rot, int waterY) {
        boolean boat = waterY >= 0;
        int sx = s.getSize().getBlockX(), sz = s.getSize().getBlockZ(), h = s.getSize().getBlockY();
        boolean swap = rot == StructureRotation.CLOCKWISE_90 || rot == StructureRotation.COUNTERCLOCKWISE_90;
        int fw = swap ? sz : sx, fd = swap ? sx : sz;      // emprise une fois tourné
        int mx = x - fw / 2, mz = z - fd / 2;              // coin bas de l'emprise
        // Le gabarit tourne autour de son origine : origine choisie pour que l'emprise tombe sur (mx, mz)
        int ox, oz;
        switch (rot) {
            case CLOCKWISE_90 -> { ox = mx + sz - 1; oz = mz; }
            case CLOCKWISE_180 -> { ox = mx + sx - 1; oz = mz + sz - 1; }
            case COUNTERCLOCKWISE_90 -> { ox = mx; oz = mz + sx - 1; }
            default -> { ox = mx; oz = mz; }
        }
        try {
            s.place(new Location(w, ox, floorY, oz), false, rot, Mirror.NONE, -1, 1.0f, new Random());
        } catch (RuntimeException e) {
            Bukkit.getLogger().warning("⚔ [Raids] Gabarit impossible à poser : " + e.getMessage());
            return null;
        }
        for (int bx = mx; bx < mx + fw; bx++) {
            for (int bz = mz; bz < mz + fd; bz++) {
                // Couche du bas vide : sol ; dessous, trou d'une pente comblé de terre (pas pour un bateau)
                Block ground = w.getBlockAt(bx, floorY, bz);
                if (!boat && ground.getType().isAir()) ground.setType(Material.GRASS_BLOCK, false);
                for (int dy = 1; dy <= 14 && !boat; dy++) {
                    Block below = w.getBlockAt(bx, floorY - dy, bz);
                    if (below.getType().isSolid() && !below.isLiquid()) break;
                    below.setType(Material.DIRT, false);
                }
                for (int by = floorY; by < floorY + h; by++) {
                    Block b = w.getBlockAt(bx, by, bz);
                    Material m = b.getType();
                    if (m == Material.JIGSAW) {
                        b.setType(by == floorY && !boat ? Material.DIRT : Material.AIR, false); // raccord au terrain
                        continue;
                    }
                    if (m.isAir() || !(b.getState() instanceof Container)) continue;
                    // Coffres, tonneaux : vides, sans table de butin (anti-exploitation, §6.3)
                    BlockState st = b.getState();
                    if (st instanceof Lootable l && l.getLootTable() != null) {
                        l.setLootTable(null);
                        st.update(true, false);
                    }
                    if (b.getState() instanceof Container c) c.getInventory().clear();
                }
            }
        }
        if (boat) refill(w, mx, mz, fw, fd, floorY, waterY);
        return new BoundingBox(mx, floorY, mz, mx + fw, floorY + h, mz + fd);
    }

    /**
     * Sous la surface, couche par couche : l'air joignable depuis le bord de l'emprise (hors de la
     * coque) redevient de l'eau ; l'air enfermé par la coque (cale, cabine) reste sec.
     */
    private static void refill(World w, int mx, int mz, int fw, int fd, int fromY, int waterY) {
        for (int y = fromY; y <= waterY; y++) {
            boolean[][] seen = new boolean[fw][fd];
            java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
            for (int i = 0; i < fw; i++) for (int j = 0; j < fd; j++) {
                if (i == 0 || j == 0 || i == fw - 1 || j == fd - 1) queue.add(new int[]{i, j});
            }
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                int i = c[0], j = c[1];
                if (i < 0 || j < 0 || i >= fw || j >= fd || seen[i][j]) continue;
                seen[i][j] = true;
                Block b = w.getBlockAt(mx + i, y, mz + j);
                if (!b.getType().isAir() && !b.isLiquid()) continue; // coque : on s'arrête là
                if (b.getType().isAir()) b.setType(Material.WATER, false);
                queue.add(new int[]{i + 1, j});
                queue.add(new int[]{i - 1, j});
                queue.add(new int[]{i, j + 1});
                queue.add(new int[]{i, j - 1});
            }
        }
    }

    /**
     * Bannières, portes (moitié basse) et coffres d'une boîte posée par le plugin (objectifs et
     * postes des défenseurs). Lecture bloc à bloc : à n'appeler que sur des boîtes de taille
     * raisonnable (tours, sièges).
     */
    static void collect(World w, BoundingBox box, List<Location> banners, List<Location> doors, List<Location> containers) {
        for (int x = (int) box.getMinX(); x < (int) box.getMaxX(); x++) {
            for (int z = (int) box.getMinZ(); z < (int) box.getMaxZ(); z++) {
                for (int y = (int) box.getMinY(); y < (int) box.getMaxY(); y++) {
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (m.isAir()) continue;
                    String n = m.name();
                    if (n.endsWith("_BANNER")) banners.add(b.getLocation());
                    else if (n.endsWith("_DOOR") && b.getBlockData() instanceof org.bukkit.block.data.Bisected bi
                            && bi.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM) doors.add(b.getLocation());
                    else if (b.getState() instanceof Container) containers.add(b.getLocation());
                }
            }
        }
    }

    /** Toutes les places où se tenir dans une colonne de la boîte (sols, étages, chemins de ronde). */
    static List<Location> standsIn(World w, int x, int z, BoundingBox box) {
        List<Location> out = new ArrayList<>();
        for (int y = (int) Math.floor(box.getMinY()) + 1; y < (int) Math.ceil(box.getMaxY()); y++) {
            Block below = w.getBlockAt(x, y - 1, z), feet = w.getBlockAt(x, y, z), head = w.getBlockAt(x, y + 1, z);
            if (below.isLiquid() || !below.getType().isSolid()) continue;
            if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) continue;
            out.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return out;
    }

    /** Une place au hasard dans la boîte (un étage au hasard de la colonne), ou null. */
    static Location randomStand(World w, BoundingBox box) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = (int) Math.floor(rnd.nextDouble(box.getMinX() + 1, Math.max(box.getMinX() + 2, box.getMaxX() - 1)));
            int z = (int) Math.floor(rnd.nextDouble(box.getMinZ() + 1, Math.max(box.getMinZ() + 2, box.getMaxZ() - 1)));
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            List<Location> stands = standsIn(w, x, z, box);
            if (!stands.isEmpty()) return stands.get(rnd.nextInt(stands.size()));
        }
        return null;
    }
}
