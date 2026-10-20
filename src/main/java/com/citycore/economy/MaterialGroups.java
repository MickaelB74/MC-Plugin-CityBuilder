package com.citycore.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

import java.util.*;
import java.util.regex.Pattern;

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
     * Résout un jeton de config : "IRON_INGOT", "#LOGS", ou toute écriture de {@link #resolveAny}.
     * @return l'ensemble des matériaux, ou null si invalide
     */
    public static Set<Material> resolve(String token) {
        Set<Material> mats = resolveAny(token);
        return mats.isEmpty() ? null : mats;
    }

    /**
     * Résout une liste de jetons séparés par des virgules, dans l'ordre (le premier matériau sert
     * d'icône et de représentant) :
     * <ul>
     *   <li>« IRON_INGOT », « minecraft:iron_ingot » ou « tide:carp » : un objet (vanilla ou mod) ;</li>
     *   <li>« #LOGS » : groupe du plugin (voir {@link #get}) ;</li>
     *   <li>« #wool », « #minecraft:planks », « #c:ingots » : tag d'objets, sinon de blocs ;</li>
     *   <li>« handcrafted:*_chair », « furniture:lamp_* » : motif sur la clé (« * » = n'importe quoi) ;</li>
     *   <li>« -valarian_conquest:nomad_citizenship » : retire ce qui suit le « - » de l'ensemble.</li>
     * </ul>
     * Pour un groupe, un tag ou un motif, le chêne et le blanc passent en tête (icône lisible).
     *
     * @return ensemble ordonné, vide si rien n'est reconnu (mod absent, faute de frappe)
     */
    public static Set<Material> resolveAny(String tokens) {
        Set<Material> out = new LinkedHashSet<>(); // pas d'EnumSet : matériaux de mods ajoutés à chaud
        Set<Material> removed = new HashSet<>();
        if (tokens == null) return out;
        for (String raw : tokens.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            boolean remove = token.startsWith("-");
            if (remove) token = token.substring(1).trim();
            Set<Material> mats = single(token);
            (remove ? removed : out).addAll(mats);
        }
        out.removeAll(removed);
        return out;
    }

    /** Familles déjà résolues (non vides) : les motifs parcourent tous les matériaux du serveur. */
    private static final Map<String, Set<Material>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Comme {@link #resolveAny}, gardé en mémoire une fois résolu (récits évalués à chaque coup ou
     * déplacement). Un résultat vide n'est pas gardé : les tags arrivent avec le chargement du serveur.
     */
    public static Set<Material> cached(String tokens) {
        Set<Material> hit = CACHE.get(tokens);
        if (hit != null) return hit;
        Set<Material> mats = Collections.unmodifiableSet(resolveAny(tokens));
        if (!mats.isEmpty()) CACHE.put(tokens, mats);
        return mats;
    }

    /** Un jeton de {@link #resolveAny}, sans virgule ni « - ». */
    private static Set<Material> single(String token) {
        if (token.startsWith("#")) {
            String name = token.substring(1);
            Set<Material> group = name.contains(":") ? null : get(name);
            if (group != null) return sorted(group);
            NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            if (key == null) return Set.of();
            try {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
                if (tag == null || tag.getValues().isEmpty()) tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
                return tag == null ? Set.of() : sorted(tag.getValues());
            } catch (RuntimeException | LinkageError e) {
                return Set.of(); // tags indisponibles (serveur en démarrage)
            }
        }
        if (token.contains("*")) {
            String pattern = token.toLowerCase(Locale.ROOT);
            if (!pattern.contains(":")) pattern = "minecraft:" + pattern;
            Pattern regex = Pattern.compile(Pattern.quote(pattern).replace("*", "\\E[a-z0-9_./-]*\\Q"));
            List<Material> found = new ArrayList<>();
            for (Material m : Material.values()) {
                NamespacedKey key = com.citycore.integration.ItemIds.keyOf(m);
                if (key != null && m.isItem() && regex.matcher(key.toString()).matches()) found.add(m);
            }
            return sorted(found);
        }
        Material mat = com.citycore.integration.ItemIds.resolve(token);
        return mat == null ? Set.of() : Set.of(mat);
    }

    /** Ordre stable des membres d'un groupe : chêne et blanc d'abord, puis l'ordre alphabétique des clés. */
    private static Set<Material> sorted(Collection<Material> mats) {
        List<Material> list = new ArrayList<>(mats);
        list.sort(Comparator.comparingInt(MaterialGroups::rank).thenComparing(Material::name));
        return new LinkedHashSet<>(list);
    }

    private static int rank(Material m) {
        NamespacedKey key = com.citycore.integration.ItemIds.keyOf(m);
        String path = key != null ? key.getKey() : m.name().toLowerCase(Locale.ROOT);
        if (path.startsWith("oak_") || path.startsWith("white_") || path.endsWith("_white")) return 0;
        return 1;
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
