package com.citycore.economy;

import org.bukkit.Material;

import java.util.*;

/**
 * Groupes de matériaux réutilisables.
 *
 * Utilisés :
 *   - dans config.yml, pour les ingrédients de boutique : "#LOGS": 2
 *   - dans les quêtes de ville (livraisons "toutes essences de bois", etc.)
 */
public final class MaterialGroups {

    public static final Set<Material> LOGS = EnumSet.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG, Material.CHERRY_LOG);

    public static final Set<Material> STEMS = EnumSet.of(
            Material.CRIMSON_STEM, Material.WARPED_STEM);

    public static final Set<Material> STONE_BRICKS = EnumSet.of(
            Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS);

    public static final Set<Material> COOKED_FOOD = EnumSet.of(
            Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_MUTTON,
            Material.COOKED_CHICKEN, Material.COOKED_RABBIT, Material.COOKED_COD,
            Material.COOKED_SALMON, Material.BREAD, Material.BAKED_POTATO);

    public static final Set<Material> STAINED_GLASS = bySuffix("_STAINED_GLASS");
    public static final Set<Material> WOOL          = bySuffix("_WOOL");

    /** Verre sous toutes ses formes : blocs, vitres, teintés (exigences des bâtiments). */
    public static final Set<Material> GLASS = glass();

    private static final Map<String, Set<Material>> BY_NAME = Map.of(
            "LOGS", LOGS,
            "STEMS", STEMS,
            "STONE_BRICKS", STONE_BRICKS,
            "COOKED_FOOD", COOKED_FOOD,
            "STAINED_GLASS", STAINED_GLASS,
            "WOOL", WOOL,
            "GLASS", GLASS);

    private MaterialGroups() {}

    /** Groupe par nom (sans le #), ou null. */
    public static Set<Material> get(String name) {
        return BY_NAME.get(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Résout un jeton de config : "IRON_INGOT" ou "#LOGS".
     * @return l'ensemble des matériaux, ou null si invalide
     */
    public static Set<Material> resolve(String token) {
        if (token.startsWith("#")) return get(token.substring(1));
        Material mat = Material.matchMaterial(token);
        return mat == null ? null : EnumSet.of(mat);
    }

    private static Set<Material> glass() {
        Set<Material> set = EnumSet.of(Material.GLASS, Material.TINTED_GLASS, Material.GLASS_PANE);
        set.addAll(bySuffix("_STAINED_GLASS"));
        set.addAll(bySuffix("_STAINED_GLASS_PANE"));
        return Collections.unmodifiableSet(set);
    }

    private static Set<Material> bySuffix(String suffix) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.name().endsWith(suffix)) set.add(m);
        }
        return Collections.unmodifiableSet(set);
    }
}
