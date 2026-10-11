package com.citycore.building;

import org.bukkit.Material;

import java.text.Normalizer;
import java.util.List;

/**
 * Types de bâtiments de la ville (phase 1 du plan « Valmont vivante »).
 *
 * La liste est fixe : chaque type porte un comportement codé (les artisans s'installent dans un
 * atelier, les habitants dans une habitation, la mairie est unique et servira de point de
 * ralliement des sièges). L'ordre de déclaration est l'ordre d'affichage des listes.
 */
public enum BuildingType {

    HABITATION("Habitation", "§a", Material.RED_BED,
            List.of("Maison des habitants de la ville.", "Lits, porte, éclairage et toit.")),
    ATELIER("Atelier", "§6", Material.CRAFTING_TABLE,
            List.of("Logis et commerce d'un artisan.", "Peut être réservé à un personnage.")),
    ANNEXE("Annexe", "§3", Material.SCAFFOLDING,
            List.of("Lieu de travail des employés d'un artisan.", "Se crée depuis ses Actions.")),
    MAIRIE("Mairie", "§e", Material.BELL,
            List.of("Siège d'Alderic et de la Charte.", "Une seule par ville.")),
    DIVERS("Divers", "§7", Material.BRICKS,
            List.of("Entrepôt, place, rempart, décor…", "Aucun habitant ne s'y installe."));

    public final String       label;
    public final String       color;
    public final Material     icon;
    public final List<String> description;

    BuildingType(String label, String color, Material icon, List<String> description) {
        this.label       = label;
        this.color       = color;
        this.icon        = icon;
        this.description = description;
    }

    /** Nom coloré (« §aHabitation »). */
    public String coloredLabel() {
        return color + label;
    }

    /** Clé de commande (« habitation ») : le nom sans accent, en minuscules. */
    public String key() {
        return name().toLowerCase();
    }

    /** Type écrit dans une commande ou lu en base (casse et accents ignorés), ou null. */
    public static BuildingType parse(String text) {
        if (text == null) return null;
        String plain = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase();
        for (BuildingType type : values()) {
            if (type.name().equals(plain)) return type;
        }
        return null;
    }

    /** Type rattaché à un artisan (atelier réservé, annexe de travail). */
    public boolean hasArtisan() {
        return this == ATELIER || this == ANNEXE;
    }

    /** Types proposés à la création et au changement de type (l'annexe se crée depuis les Actions de l'artisan). */
    public static List<BuildingType> choosable() {
        return java.util.Arrays.stream(values()).filter(t -> t != ANNEXE).toList();
    }
}
