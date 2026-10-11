package com.citycore.journeymap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contours d'un ensemble de chunks : les parcelles de la ville deviennent des polygones (bord
 * extérieur et trous), comme les dessine JourneyMap.
 *
 * Principe : chaque chunk apporte ses quatre côtés orientés (parcelle toujours à gauche du sens
 * de parcours) ; un côté partagé avec une autre parcelle disparaît. Les côtés restants forment
 * des boucles fermées. Orientation de JourneyMap ({@code PolygonHelper}) : aire signée positive
 * pour un bord extérieur, négative pour un trou.
 *
 * Classe sans dépendance à Minecraft (testable seule). Coordonnées des résultats : en blocs.
 */
final class ChunkOutline {

    /** Une boucle fermée de coins (x, z en blocs) et le double de son aire signée. */
    record Ring(List<int[]> points, long area2) {}

    /** Une zone d'un seul tenant : son bord extérieur et ses trous éventuels. */
    record Shape(Ring outer, List<Ring> holes) {}

    // Directions sur la carte (x vers l'est, z vers le sud) : est, nord, ouest, sud
    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DZ = {0, -1, 0, 1};

    private ChunkOutline() {
    }

    /**
     * Contours des chunks donnés.
     *
     * @param chunks coordonnées de chunk {x, z}
     * @return les zones, chacune avec son bord extérieur et ses trous
     */
    static List<Shape> of(Collection<long[]> chunks) {
        Set<Long> claimed = new HashSet<>();
        for (long[] c : chunks) claimed.add(key((int) c[0], (int) c[1]));

        // Côtés de bordure, rangés par coin de départ (unités : chunks) → directions sortantes
        Map<Long, List<Integer>> out = new HashMap<>();
        for (long k : claimed) {
            int cx = kx(k), cz = kz(k);
            // Sens du parcours : sud-ouest → sud-est → nord-est → nord-ouest (parcelle à gauche)
            if (!claimed.contains(key(cx, cz + 1))) add(out, cx, cz + 1, 0);         // côté sud, vers l'est
            if (!claimed.contains(key(cx + 1, cz))) add(out, cx + 1, cz + 1, 1);     // côté est, vers le nord
            if (!claimed.contains(key(cx, cz - 1))) add(out, cx + 1, cz, 2);         // côté nord, vers l'ouest
            if (!claimed.contains(key(cx - 1, cz))) add(out, cx, cz, 3);             // côté ouest, vers le sud
        }

        List<Ring> outers = new ArrayList<>();
        List<Ring> holes  = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Map.Entry<Long, List<Integer>> start : out.entrySet()) {
            for (int startDir : start.getValue()) {
                if (used.contains(edgeKey(start.getKey(), startDir))) continue;
                Ring ring = trace(out, used, start.getKey(), startDir);
                (ring.area2() > 0 ? outers : holes).add(ring);
            }
        }

        // Chaque trou rejoint la plus petite zone qui le contient
        Map<Ring, List<Ring>> holesOf = new HashMap<>();
        for (Ring o : outers) holesOf.put(o, new ArrayList<>());
        for (Ring h : holes) {
            double[] p = insidePoint(h);
            Ring best = null;
            for (Ring o : outers) {
                if (!contains(o, p[0], p[1])) continue;
                if (best == null || Math.abs(o.area2()) < Math.abs(best.area2())) best = o;
            }
            if (best != null) holesOf.get(best).add(h);
        }
        List<Shape> shapes = new ArrayList<>();
        for (Ring o : outers) shapes.add(new Shape(o, holesOf.get(o)));
        return shapes;
    }

    /**
     * Suit une boucle à partir d'un côté. À un coin où deux parcelles se touchent en diagonale
     * (deux côtés sortants), on tourne vers la parcelle d'où l'on vient (à gauche) : les deux
     * parcelles restent des zones distinctes, sans contour qui se croise.
     */
    private static Ring trace(Map<Long, List<Integer>> out, Set<Long> used, long startVertex, int startDir) {
        List<int[]> corners = new ArrayList<>();   // coins en unités de chunk, sans points alignés
        long vertex = startVertex;
        int dir = startDir;
        int prevDir = -1;
        long area2 = 0;
        int guard = 0;
        do {
            used.add(edgeKey(vertex, dir));
            int x = kx(vertex), z = kz(vertex);
            if (dir != prevDir) corners.add(new int[]{x, z});
            int nx = x + DX[dir], nz = z + DZ[dir];
            // Formule de l'aire signée de JourneyMap : somme de (bx − ax)(bz + az)
            area2 += (long) (nx - x) * (nz + z);
            prevDir = dir;
            vertex = key(nx, nz);
            dir = nextDir(out.get(vertex), dir);
            if (++guard > 4_000_000) throw new IllegalStateException("contour sans fin");
        } while (!(vertex == startVertex && dir == startDir));
        // Le premier coin est inutile si le dernier côté va dans le même sens que le premier
        if (prevDir == startDir && corners.size() > 1) corners.remove(0);

        List<int[]> blocks = new ArrayList<>(corners.size());
        for (int[] c : corners) blocks.add(new int[]{c[0] * 16, c[1] * 16});
        return new Ring(blocks, area2 * 256);
    }

    /** Côté suivant : à gauche d'abord, puis tout droit, puis à droite (jamais demi-tour). */
    private static int nextDir(List<Integer> options, int dir) {
        if (options == null || options.isEmpty()) throw new IllegalStateException("contour ouvert");
        int left = (dir + 1) % 4, right = (dir + 3) % 4;
        if (options.contains(left)) return left;
        if (options.contains(dir)) return dir;
        if (options.contains(right)) return right;
        throw new IllegalStateException("contour sans suite");
    }

    /** Un point à l'intérieur du trou : à droite du premier côté (côté non revendiqué), en blocs. */
    private static double[] insidePoint(Ring hole) {
        int[] a = hole.points().get(0), b = hole.points().get(1 % hole.points().size());
        double dx = Integer.signum(b[0] - a[0]), dz = Integer.signum(b[1] - a[1]);
        // À droite du sens de parcours (sur la carte) : (−dz, dx)
        return new double[]{a[0] + dx * 8 - dz * 4, a[1] + dz * 8 + dx * 4};
    }

    /** Point dans une boucle (lancer de rayon ; le point n'est jamais sur un bord). */
    private static boolean contains(Ring ring, double px, double pz) {
        List<int[]> pts = ring.points();
        boolean inside = false;
        for (int i = 0, j = pts.size() - 1; i < pts.size(); j = i++) {
            double xi = pts.get(i)[0], zi = pts.get(i)[1], xj = pts.get(j)[0], zj = pts.get(j)[1];
            if ((zi > pz) != (zj > pz) && px < (xj - xi) * (pz - zi) / (zj - zi) + xi) inside = !inside;
        }
        return inside;
    }

    private static void add(Map<Long, List<Integer>> out, int x, int z, int dir) {
        out.computeIfAbsent(key(x, z), k -> new ArrayList<>(2)).add(dir);
    }

    private static long edgeKey(long vertex, int dir) {
        return vertex * 4 + dir;
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int kx(long key) {
        return (int) (key >> 32);
    }

    private static int kz(long key) {
        return (int) key;
    }
}
