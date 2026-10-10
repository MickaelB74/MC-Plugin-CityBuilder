package com.citycore.integration;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Identifiants d'objets et de créatures, vanilla ou ajoutés par des mods.
 *
 * Sur un serveur hybride (Youer, Mohist : mods NeoForge + plugins), chaque objet de mod devient
 * une valeur de {@link Material} : « dmr:dragon_egg » s'appelle DMR_DRAGON_EGG (":" devient "_",
 * les autres signes disparaissent) et garde sa clé d'origine. config.yml accepte donc les deux
 * écritures : OAK_LOG / minecraft:oak_log pour le jeu de base, « mod:objet » pour un mod.
 */
public final class ItemIds {

    private ItemIds() {}

    /** Objets de mods indexés par leur clé (construit au premier besoin). */
    private static Map<NamespacedKey, Material> modded;

    /** Noms français des objets de mods connus (affichage des boutiques et quêtes). */
    private static final Map<String, String> LABELS = Map.of(
            "dmr:dragon_egg", "Œuf de dragon",
            "dmr:blank_egg", "Œuf de dragon vierge",
            "dmr:dragon_armor", "Armure de dragon",
            "dmr:dragon_spawn_egg", "Œuf d'apparition de dragon");

    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("white", "blanc"), Map.entry("orange", "orange"), Map.entry("magenta", "magenta"),
            Map.entry("light_blue", "bleu clair"), Map.entry("yellow", "jaune"), Map.entry("lime", "vert clair"),
            Map.entry("pink", "rose"), Map.entry("gray", "gris"), Map.entry("light_gray", "gris clair"),
            Map.entry("cyan", "cyan"), Map.entry("purple", "violet"), Map.entry("blue", "bleu"),
            Map.entry("brown", "marron"), Map.entry("green", "vert"), Map.entry("red", "rouge"),
            Map.entry("black", "noir"));

    /**
     * Matériau désigné par un identifiant de config.yml, ou null s'il n'existe pas sur ce
     * serveur (faute de frappe, ou mod absent).
     */
    public static Material resolve(String id) {
        if (id == null || id.isBlank()) return null;
        String s = id.trim();
        if (!isModId(s)) return Material.matchMaterial(s);
        // Nom donné par le serveur hybride, puis clé d'origine (au cas où le nommage diffère)
        Material byName = Material.getMaterial(hybridName(s));
        if (byName != null) return byName;
        NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
        return key == null ? null : moddedMaterials().get(key);
    }

    /** « mod:objet » (espace de noms autre que minecraft). */
    public static boolean isModId(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        return colon > 0 && !id.substring(0, colon).equalsIgnoreCase(NamespacedKey.MINECRAFT);
    }

    /** Espace de noms d'un identifiant (« dmr » pour « dmr:dragon_egg »), minecraft par défaut. */
    public static String namespace(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        return colon > 0 ? id.substring(0, colon).toLowerCase(Locale.ROOT) : NamespacedKey.MINECRAFT;
    }

    /** Nom de l'énumération créé par Youer / Mohist pour un objet de mod. */
    public static String hybridName(String id) {
        return id.replace(':', '_').replaceAll("\\s+", "_").replaceAll("\\W", "").toUpperCase(Locale.ENGLISH);
    }

    /** Clé d'un matériau (null pour les anciens matériaux « LEGACY », qui n'en ont pas). */
    public static NamespacedKey keyOf(Material mat) {
        if (mat == null || mat.isLegacy()) return null;
        try {
            return mat.getKey();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Objet ajouté par un mod ? */
    public static boolean isModded(Material mat) {
        NamespacedKey key = keyOf(mat);
        return key != null && !NamespacedKey.MINECRAFT.equals(key.getNamespace());
    }

    /**
     * Nom affiché d'un matériau : « Oak Log » pour le jeu de base (comme jusqu'ici), nom
     * français pour les objets de mods connus, sinon le chemin de la clé (« Dragon Whistle »).
     */
    public static String label(Material mat) {
        if (mat == null) return "?";
        NamespacedKey key = keyOf(mat);
        if (key == null || NamespacedKey.MINECRAFT.equals(key.getNamespace())) return words(mat.name());
        String full = key.toString();
        String known = LABELS.get(full);
        if (known != null) return known;
        // Sifflets de Dragon Mounts : « dmr:dragon_whistle.red » → « Sifflet de dragon rouge »
        if (full.startsWith("dmr:dragon_whistle.")) {
            String color = full.substring("dmr:dragon_whistle.".length());
            return "Sifflet de dragon " + COLORS.getOrDefault(color, color.replace('_', ' '));
        }
        return words(key.getKey().replace('.', '_'));
    }

    /** « OAK_LOG » → « Oak Log ». */
    public static String words(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split("_")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT)).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * La créature est-elle du type demandé (« dmr:dragon ») ? On compare la clé du type, puis le
     * nom donné par le serveur hybride ; en dernier recours, la classe de l'entité du mod.
     */
    public static boolean entityIs(Entity entity, String id, String modClassName) {
        if (entity == null) return false;
        EntityType type = entity.getType();
        try {
            NamespacedKey key = type.getKey();
            if (key != null && key.toString().equalsIgnoreCase(id)) return true;
        } catch (RuntimeException ignored) {
            // type inconnu (UNKNOWN) : pas de clé
        }
        if (type.name().equals(hybridName(id))) return true;
        if (modClassName == null) return false;
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            for (Class<?> c = handle.getClass(); c != null; c = c.getSuperclass()) {
                if (c.getName().equals(modClassName)) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // pas de poignée accessible : on s'en tient aux deux premiers tests
        }
        return false;
    }

    private static synchronized Map<NamespacedKey, Material> moddedMaterials() {
        if (modded == null) {
            Map<NamespacedKey, Material> map = new HashMap<>();
            for (Material mat : Material.values()) {
                NamespacedKey key = keyOf(mat);
                if (key != null && !NamespacedKey.MINECRAFT.equals(key.getNamespace())) map.put(key, mat);
            }
            modded = map;
        }
        return modded;
    }
}
