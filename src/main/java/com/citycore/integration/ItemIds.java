package com.citycore.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Identifiants d'objets et de créatures, vanilla ou ajoutés par des mods.
 *
 * Sur un serveur hybride (Youer, Mohist : mods NeoForge + plugins), chaque objet de mod devient
 * une valeur de {@link Material} : « dmr:dragon_egg » s'appelle DMR_DRAGON_EGG (":" devient "_",
 * les autres signes disparaissent) et garde sa clé d'origine. Les créatures des mods deviennent de
 * même des valeurs d'{@link EntityType} (« irons_spellbooks:cultist » → IRONS_SPELLBOOKS_CULTIST).
 * config.yml accepte donc les deux écritures : OAK_LOG / minecraft:oak_log pour le jeu de base,
 * « mod:objet » pour un mod.
 *
 * Noms affichés des objets et créatures de mods : table française du plugin
 * (resources/integration/mod-labels.yml), puis fichiers de langue des mods eux-mêmes (fr_fr,
 * sinon en_us, lus dans les jars du dossier mods du serveur), enfin le chemin de la clé.
 */
public final class ItemIds {

    private ItemIds() {}

    /** Objets de mods indexés par leur clé (construit au premier besoin). */
    private static Map<NamespacedKey, Material> modded;
    /** Créatures indexées par leur clé (construit au premier besoin). */
    private static Map<String, EntityType> entities;

    /** Noms français choisis par le plugin : « mod:objet » → nom (objets et créatures). */
    private static Map<String, String> labels;
    /** Noms tirés des fichiers de langue des mods : « mod:objet » → nom. */
    private static Map<String, String> langNames;

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

    /**
     * Type de créature désigné par un identifiant (« ZOMBIE », « minecraft:zombie »,
     * « mowziesmobs:naga »), ou null s'il n'existe pas sur ce serveur.
     */
    public static EntityType resolveEntity(String id) {
        if (id == null || id.isBlank()) return null;
        String s = id.trim();
        if (!s.contains(":")) {
            try {
                return EntityType.valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (!isModId(s)) s = s.substring(s.indexOf(':') + 1);
        try {
            return EntityType.valueOf(hybridName(s));
        } catch (IllegalArgumentException ignored) {
            // nom différent sur ce serveur : on cherche par la clé
        }
        return entityTypes().get(s.contains(":") ? s.toLowerCase(Locale.ROOT) : "minecraft:" + s.toLowerCase(Locale.ROOT));
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

    /** Clé d'un type de créature (« mowziesmobs:naga »), ou null. */
    public static String keyOf(EntityType type) {
        if (type == null) return null;
        try {
            NamespacedKey key = type.getKey();
            return key == null ? null : key.toString();
        } catch (RuntimeException e) {
            return null; // UNKNOWN : pas de clé
        }
    }

    /** Objet ajouté par un mod ? */
    public static boolean isModded(Material mat) {
        NamespacedKey key = keyOf(mat);
        return key != null && !NamespacedKey.MINECRAFT.equals(key.getNamespace());
    }

    /**
     * Nom affiché d'un matériau : « Oak Log » pour le jeu de base (comme jusqu'ici), nom
     * français pour les objets de mods connus, sinon le nom du fichier de langue du mod, ou le
     * chemin de la clé (« Dragon Whistle »).
     */
    public static String label(Material mat) {
        if (mat == null) return "?";
        NamespacedKey key = keyOf(mat);
        if (key == null || NamespacedKey.MINECRAFT.equals(key.getNamespace())) return words(mat.name());
        String full = key.toString();
        String known = labels().get(full);
        if (known != null) return known;
        // Sifflets de Dragon Mounts : « dmr:dragon_whistle.red » → « Sifflet de dragon rouge »
        if (full.startsWith("dmr:dragon_whistle.")) {
            String color = full.substring("dmr:dragon_whistle.".length());
            return "Sifflet de dragon " + COLORS.getOrDefault(color, color.replace('_', ' '));
        }
        String lang = langNames().get(full);
        if (lang != null) return lang;
        return words(key.getKey().replace('.', '_'));
    }

    /** Nom affiché d'une créature : « Zombie Villager » (jeu de base), nom français pour un mod. */
    public static String entityLabel(EntityType type) {
        if (type == null) return "?";
        String key = keyOf(type);
        if (key == null || key.startsWith("minecraft:")) return words(type.name());
        String known = labels().get(key);
        if (known != null) return known;
        String lang = langNames().get(key);
        if (lang != null) return lang;
        return words(key.substring(key.indexOf(':') + 1));
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
        String key = keyOf(type);
        if (key != null && key.equalsIgnoreCase(id)) return true;
        if (type.name().equals(hybridName(id))) return true;
        return modClassName != null && handleIs(entity, modClassName);
    }

    /** La créature vient-elle de ce mod (espace de noms de son type) ? */
    public static boolean entityFrom(Entity entity, String namespace) {
        if (entity == null) return false;
        String key = keyOf(entity.getType());
        if (key != null) return key.startsWith(namespace.toLowerCase(Locale.ROOT) + ":");
        return entity.getType().name().startsWith(hybridName(namespace) + "_");
    }

    /** L'entité du serveur (sous l'enveloppe Bukkit) est-elle de cette classe ou d'une sous-classe ? */
    public static boolean handleIs(Entity entity, String className) {
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            for (Class<?> c = handle.getClass(); c != null; c = c.getSuperclass()) {
                if (c.getName().equals(className)) return true;
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

    private static synchronized Map<String, EntityType> entityTypes() {
        if (entities == null) {
            Map<String, EntityType> map = new HashMap<>();
            for (EntityType type : EntityType.values()) {
                String key = keyOf(type);
                if (key != null) map.put(key, type);
            }
            entities = map;
        }
        return entities;
    }

    /* =========================
       NOMS FRANÇAIS
       ========================= */

    /** Table du plugin (sections « objets » et « creatures » de mod-labels.yml). */
    private static synchronized Map<String, String> labels() {
        if (labels == null) {
            Map<String, String> map = new HashMap<>();
            try (InputStream in = ItemIds.class.getClassLoader().getResourceAsStream("integration/mod-labels.yml")) {
                if (in != null) {
                    YamlConfiguration yml = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String section : new String[]{"objets", "creatures"}) {
                        var s = yml.getConfigurationSection(section);
                        if (s == null) continue;
                        // Clés « mod:objet » : le « : » n'est pas un séparateur de YAML entre guillemets
                        for (String k : s.getKeys(false)) map.put(k.toLowerCase(Locale.ROOT), s.getString(k));
                    }
                }
            } catch (Exception e) {
                // table illisible : noms tirés des mods
            }
            labels = map;
        }
        return labels;
    }

    /**
     * Noms tirés des fichiers de langue des mods connus (fr_fr d'abord, sinon en_us), lus une fois
     * dans les jars du dossier mods du serveur. Rien sur un serveur sans ce dossier.
     */
    private static synchronized Map<String, String> langNames() {
        if (langNames == null) {
            Map<String, String> map = new HashMap<>();
            try {
                File mods = new File(Bukkit.getWorldContainer(), "mods");
                File[] jars = mods.isDirectory() ? mods.listFiles((d, n) -> n.endsWith(".jar")) : null;
                if (jars != null) {
                    for (File jar : jars) readLang(jar, map);
                }
            } catch (RuntimeException | LinkageError e) {
                // dossier illisible : chemin de la clé
            }
            langNames = map;
        }
        return langNames;
    }

    private static void readLang(File jar, Map<String, String> into) {
        try (ZipFile zip = new ZipFile(jar)) {
            for (ModIntegrations.Mod mod : ModIntegrations.Mod.values()) {
                ZipEntry entry = zip.getEntry("assets/" + mod.id + "/lang/fr_fr.json");
                if (entry == null) entry = zip.getEntry("assets/" + mod.id + "/lang/en_us.json");
                if (entry == null) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    for (var e : json.entrySet()) {
                        String[] parts = e.getKey().split("\\.", 3);
                        if (parts.length != 3 || !e.getValue().isJsonPrimitive()) continue;
                        if (!parts[0].equals("item") && !parts[0].equals("block") && !parts[0].equals("entity")) continue;
                        // les objets priment sur les blocs du même nom
                        String id = parts[1] + ":" + parts[2];
                        if (parts[0].equals("block") && into.containsKey(id)) continue;
                        into.put(id, e.getValue().getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
            // jar illisible : on passe au suivant
        }
    }
}
