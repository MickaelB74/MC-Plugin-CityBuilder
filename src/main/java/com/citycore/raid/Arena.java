package com.citycore.raid;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ce que le balayage a trouvé d'une cible posée (PLAN_RAIDS §2.7) : son emprise, ses points
 * remarquables et ses occupants. Les objectifs (cloche, bannières, cages) et les défenseurs des
 * phases suivantes s'appuient dessus.
 *
 * @param structure  identifiant posé
 * @param box        étendue des blocs posés par la structure (différence avant / après la pose)
 * @param center     cloche s'il y en a une, sinon centre de la boîte au sol
 * @param bells      cloches
 * @param banners    bannières (y compris celles des mods, ex. bannières suspendues de Valarian Conquest)
 * @param spawners   cages à monstres
 * @param containers coffres, tonneaux, pots décorés, boîtes de shulker
 * @param doors      portes (bloc du bas)
 * @param beds       nombre de lits
 * @param defenders  défenseurs (étiquette {@link ArenaScanner#TAG_DEFENDER})
 * @param civilians  civils (étiquette {@link ArenaScanner#TAG_CIVIL})
 * @param golems     golems de fer et de neige (à retirer avant l'assaut)
 * @param gates      portes et pont-levis de Valarian Conquest (entités)
 * @param byKind     occupants par catégorie puis par clé de type (« valarian_conquest:soldier » → 19)
 */
public record Arena(String structure, World world, BoundingBox box, Location center,
                    List<Location> bells, List<Location> banners, List<Location> spawners,
                    List<Location> containers, List<Location> doors, int beds,
                    List<UUID> defenders, List<UUID> civilians, List<UUID> golems, List<UUID> gates,
                    Map<String, Map<String, Integer>> byKind) {

    /**
     * Même arène sur une zone agrandie (ville fortifiée : village, tours et siège), avec les
     * bannières, portes et coffres des pièces posées par le plugin.
     */
    Arena extend(BoundingBox zone, List<Location> moreBanners, List<Location> moreDoors, List<Location> moreContainers) {
        List<Location> b = new ArrayList<>(banners), d = new ArrayList<>(doors), c = new ArrayList<>(containers);
        b.addAll(moreBanners);
        d.addAll(moreDoors);
        c.addAll(moreContainers);
        return new Arena(structure, world, zone, center, bells, b, spawners, c, d, beds, defenders, civilians, golems, gates, byKind);
    }

    /** Taille de la boîte : « 66×36×80 ». */
    public String size() {
        if (box == null) return "?";
        return (int) box.getWidthX() + "×" + (int) box.getHeight() + "×" + (int) box.getWidthZ();
    }

    /** Lignes du journal du balayage (console et administrateur). */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        if (box == null) {
            lines.add("aucun bloc posé par la structure (pose sans effet ?)");
        } else {
            lines.add("boîte " + size() + " (x " + (int) box.getMinX() + ".." + (int) box.getMaxX()
                    + ", y " + (int) box.getMinY() + ".." + (int) box.getMaxY()
                    + ", z " + (int) box.getMinZ() + ".." + (int) box.getMaxZ() + ") ; centre "
                    + (bells.isEmpty() ? "" : "(cloche) ") + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ());
        }
        for (Map.Entry<String, Map<String, Integer>> e : byKind.entrySet()) {
            int total = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0) continue;
            lines.add(e.getKey() + " " + total + " : " + e.getValue().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(o -> o.getKey() + " ×" + o.getValue()).collect(Collectors.joining(", ")));
        }
        lines.add("blocs : cloches " + bells.size() + ", bannières " + banners.size() + ", cages " + spawners.size()
                + ", coffres et tonneaux " + containers.size() + ", portes " + doors.size() + ", lits " + beds);
        return lines;
    }
}
