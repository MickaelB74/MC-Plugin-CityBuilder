package com.citycore.raid;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Villes fortifiées (PLAN_RAIDS §7.8, phase R6, refaite sans /place le 21/10/2026) : un village né
 * de la génération, que le plugin entoure du siège du pouvoir (du côté opposé au camp) et de tours
 * de la faction (sur les deux côtés et du côté du camp, vers le coin le plus éloigné de lui). Les
 * gabarits sont posés tels quels, sans leurs occupants : la faction de la ville les garde.
 *
 * Les chunks doivent être chargés : la zone générée autour du village est agrandie pour contenir
 * tours et siège (voir RaidManager#startRaid).
 */
final class RaidCity {

    /** Tour posée : sa boîte, et le haut de sa colonne du milieu (bannière de la faction). */
    record Tower(BoundingBox box, Location top) {}

    /**
     * Ville assemblée.
     *
     * @param village    boîte du village (postes de sa garnison)
     * @param zone       village, tours et siège (balayage, nettoyage)
     * @param seat       boîte du siège du pouvoir, ou null
     * @param banners    bannières des tours et du siège ; {@code doors}, {@code containers} de même
     */
    record Built(BoundingBox village, BoundingBox zone, List<Tower> towers, BoundingBox seat,
                 List<Location> banners, List<Location> doors, List<Location> containers) {}

    private RaidCity() {}

    /** Siège et tours autour du village ; null si rien n'a pu être posé (modèles absents, pas de place). */
    static Built build(World w, BoundingBox village, RaidCamp.Camp camp, RaidTarget.City city, Logger log) {
        Structure tower = RaidTemplates.load(city.tower()), seat = RaidTemplates.load(city.seat());
        if (tower == null) log.warning("⚔ [Raids] Tour « " + city.tower() + " » introuvable (gabarit absent du serveur).");
        if (seat == null) log.warning("⚔ [Raids] Siège « " + city.seat() + " » introuvable (gabarit absent du serveur).");
        double cx = village.getCenterX(), cz = village.getCenterZ();
        double vx = camp.spawn().getX() - cx, vz = camp.spawn().getZ() - cz;
        // Côté du camp : l'axe dominant ; latéral : perpendiculaire
        int dx = 0, dz = 0;
        if (Math.abs(vx) >= Math.abs(vz)) dx = vx >= 0 ? 1 : -1; else dz = vz >= 0 ? 1 : -1;
        int px = -dz, pz = dx;
        BoundingBox campArea = new BoundingBox(camp.spawn().getX() - 10, -64, camp.spawn().getZ() - 10,
                camp.spawn().getX() + 10, 320, camp.spawn().getZ() + 10);
        List<BoundingBox> taken = new ArrayList<>();
        taken.add(village);

        BoundingBox seatBox = seat == null ? null : around(w, seat, village, -dx, -dz, 0, taken, campArea);
        List<Tower> towers = new ArrayList<>();
        if (tower != null) {
            double halfLat = (dx != 0 ? village.getWidthZ() : village.getWidthX()) / 2;
            double campLat = vx * px + vz * pz;          // décalage latéral du camp
            double corner = Math.max(0, halfLat - RaidTemplates.span(tower) / 2.0 - 2) * (campLat >= 0 ? -1 : 1);
            double[][] spots = {{px, pz, 0}, {-px, -pz, 0}, {dx, dz, corner}};
            for (int i = 0; i < Math.min(3, city.towers()); i++) {
                BoundingBox b = around(w, tower, village, (int) spots[i][0], (int) spots[i][1], spots[i][2], taken, campArea);
                if (b != null) towers.add(new Tower(b, top(w, b)));
            }
        }
        if (seatBox == null && towers.isEmpty()) return null;
        BoundingBox zone = village.clone();
        List<Location> banners = new ArrayList<>(), doors = new ArrayList<>(), containers = new ArrayList<>();
        if (seatBox != null) {
            zone.union(seatBox);
            RaidTemplates.collect(w, seatBox, banners, doors, containers);
        }
        for (Tower t : towers) {
            zone.union(t.box());
            RaidTemplates.collect(w, t.box(), banners, doors, containers);
        }
        return new Built(village, zone, towers, seatBox, banners, doors, containers);
    }

    /**
     * Gabarit posé hors du village du côté (dx, dz), décalé de {@code lat} blocs sur le côté : sur la
     * terre ferme, relief de 14 blocs au plus, sans empiéter sur le camp ni sur ce qui est déjà posé
     * (jusqu'à deux reculs de 6 blocs) ; null s'il n'y a pas de place dans la zone générée.
     */
    private static BoundingBox around(World w, Structure s, BoundingBox village, int dx, int dz, double lat,
                                      List<BoundingBox> taken, BoundingBox camp) {
        int half = RaidTemplates.span(s) / 2;
        double edge = dx != 0 ? village.getWidthX() / 2 : village.getWidthZ() / 2;
        int px = -dz, pz = dx;
        for (int shift = 0; shift <= 12; shift += 6) {
            double d = edge + half + 4 + shift;
            int x = (int) Math.floor(village.getCenterX() + dx * d + px * lat);
            int z = (int) Math.floor(village.getCenterZ() + dz * d + pz * lat);
            int[] eval = RaidCamp.evaluate(w, x, z, half);
            if (eval == null) return null; // hors de la zone générée
            int columns = (2 * half + 1) * (2 * half + 1);
            if (eval[1] > columns * 0.3 || eval[0] > 14) continue;
            BoundingBox foot = new BoundingBox(x - half - 1, -64, z - half - 1, x + half + 1, 320, z + half + 1);
            if (foot.overlaps(camp) || taken.stream().anyMatch(foot::overlaps)) continue;
            BoundingBox placed = RaidTemplates.place(w, s, x, z, eval[2], RaidTemplates.randomRotation());
            if (placed == null) return null;
            taken.add(placed);
            return placed;
        }
        return null;
    }

    /** Haut de la colonne du milieu d'une tour (au-dessus de son plus haut bloc). */
    private static Location top(World w, BoundingBox b) {
        int x = (int) Math.floor(b.getCenterX()), z = (int) Math.floor(b.getCenterZ());
        int y = Math.min((int) b.getMaxY() + 1, w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
        return new Location(w, x, y, z);
    }

    /**
     * Où se tient le bailli : au rez-de-chaussée du siège, au plus près du milieu ; à défaut, une
     * place au hasard du siège ; null s'il n'y en a pas.
     */
    static Location seatSpot(World w, BoundingBox seat) {
        int cx = (int) Math.floor(seat.getCenterX()), cz = (int) Math.floor(seat.getCenterZ());
        for (int ring = 0; ring <= 6; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    List<Location> stands = RaidTemplates.standsIn(w, cx + dx, cz + dz, seat);
                    if (!stands.isEmpty()) return stands.get(0);
                }
            }
        }
        return RaidTemplates.randomStand(w, seat);
    }
}
