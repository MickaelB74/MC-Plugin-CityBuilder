package com.citycore.building;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Résultat de l'inspection d'un bâtiment (phase 2 du plan « Valmont vivante »).
 *
 * @param checks          exigences contrôlées (blocs requis, porte, toit, lumière), dans l'ordre
 * @param bonusChecks     blocs « bonus » du métier : présents (ok) ou absents
 * @param level           0 = incomplet, 1 = conforme, 2 à 5 selon les points de détail
 * @param points          total des points de détail
 * @param levelFloor      points demandés pour le niveau actuel (0 au niveau 1)
 * @param nextThreshold   points demandés pour le niveau suivant (-1 = niveau maximal ou incomplet)
 */
public record Inspection(Building building, String target, List<Check> checks, List<Check> bonusChecks,
                         int level, int points, int variety, int decorations, int bonus, int surface,
                         int levelFloor, int nextThreshold) {

    /** Une exigence ou un bonus : libellé, rempli ou non, détail (« 0/1 », « 35 % »), icône. */
    public record Check(String label, boolean ok, String detail, Material icon) {}

    public boolean compliant() {
        return level > 0;
    }

    /** Points manquants pour le niveau suivant (-1 = niveau maximal ou bâtiment incomplet). */
    public int nextLevelPoints() {
        return nextThreshold < 0 ? -1 : Math.max(0, nextThreshold - points);
    }

    /** Exigences non remplies, en clair : « 1 enclume, une porte ». */
    public String missing() {
        List<String> parts = new ArrayList<>();
        for (Check c : checks) if (!c.ok()) parts.add(c.label());
        return String.join(", ", parts);
    }

    /** Blocs bonus du métier encore absents. */
    public List<String> bonusMissing() {
        List<String> names = new ArrayList<>();
        for (Check c : bonusChecks) if (!c.ok()) names.add(c.label());
        return names;
    }

    /** « ★★★☆☆ » (doré) ou « incomplet » (rouge). */
    public String levelLabel() {
        if (!compliant()) return "§c✘ Incomplet";
        return "§6Niveau " + level + " " + stars(level);
    }

    public static String stars(int level) {
        return "§6" + "★".repeat(Math.max(0, level)) + "§8" + "☆".repeat(Math.max(0, 5 - level));
    }

    /** Une ligne : « Forge : niveau 2 ★★ » ou « Forge : incomplet — il manque 1 enclume ». */
    public String summary() {
        return compliant() ? "§e" + building.name() + " §7: " + levelLabel()
                : "§e" + building.name() + " §7: §cincomplet §7— il manque §f" + missing();
    }

    /** Bulle complète : niveau, exigences ✔/✘, points de détail, piste pour le niveau suivant. */
    public List<String> lore() {
        List<String> lines = new ArrayList<>();
        lines.add(levelLabel());
        for (Check c : checks) {
            lines.add((c.ok() ? "§a✔ §7" : "§c✘ §7") + c.label() + (c.detail().isEmpty() ? "" : " §8(" + c.detail() + ")"));
        }
        lines.add("§7Détail : §f" + points + " pts §8(variété " + variety + ", décor " + decorations
                + ", bonus " + bonus + ", surface " + surface + ")");
        if (!compliant()) {
            lines.add("§cComplétez les exigences pour atteindre le niveau 1.");
        } else if (nextLevelPoints() < 0) {
            lines.add("§6Niveau maximal atteint.");
        } else {
            lines.add("§7Niveau " + (level + 1) + " : §f+" + nextLevelPoints() + " pts §8(variez les blocs, décorez)");
        }
        List<String> missingBonus = bonusMissing();
        if (!missingBonus.isEmpty()) {
            lines.add("§8Bonus possibles : " + String.join(", ", missingBonus.subList(0, Math.min(3, missingBonus.size())))
                    + (missingBonus.size() > 3 ? "…" : ""));
        }
        return lines;
    }
}
