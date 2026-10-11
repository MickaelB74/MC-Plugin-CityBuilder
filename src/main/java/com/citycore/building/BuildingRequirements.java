package com.citycore.building;

import com.citycore.economy.MaterialGroups;
import com.citycore.npc.CityNPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Exigences des bâtiments (phase 2 du plan « Valmont vivante »), lues dans la section
 * {@code batiments} de config.yml : blocs requis par type et par artisan, blocs « bonus » du
 * métier, porte, lumière et toit, points de détail et paliers de niveau.
 *
 * Un bloc s'écrit comme dans le reste de config.yml : nom de matériau ({@code ANVIL}), groupe
 * du plugin ({@code #LOGS}, {@code #GLASS}…) ou tag de blocs de Minecraft ({@code #beds},
 * {@code #anvil}, {@code #candles}…), plusieurs séparés par des virgules.
 */
public final class BuildingRequirements {

    /** Bloc (ou groupe) attendu : libellé, matériaux acceptés, nombre minimum. */
    public record Need(String name, String plural, Set<Material> materials, int count) {
        /** « 1 enclume », « 6 blocs de laine ». */
        public String label() {
            return count + " " + (count > 1 ? plural : name);
        }
    }

    /** Exigences d'un bâtiment. Un minimum à 0 = pas d'exigence (porte, lumière, toit en %). */
    public record Spec(String key, List<Need> required, List<Need> bonus, int doors, int lights, int roofPercent) {
        public boolean hasRules() {
            return !required.isEmpty() || doors > 0 || lights > 0 || roofPercent > 0;
        }
    }

    /** Points de détail (voir config.yml, batiments.points). */
    public record Points(int variety, int varietyMax, int decoration, int decorationMax, int decorationPerType,
                         int bonus, int surface, int surfaceMax) {}

    private final JavaPlugin plugin;

    private Spec habitation, mairie, divers, freeAtelier;
    private final Map<String, Spec> ateliers = new HashMap<>();
    private final NavigableMap<Integer, Integer> levels = new TreeMap<>(); // niveau → points requis
    private final Set<Material> decorations = new HashSet<>();
    private Points points;
    private double storageBonus;

    public BuildingRequirements(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /* =========================
       LECTURE DE config.yml
       ========================= */

    public void load() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("batiments");
        if (root == null) root = plugin.getConfig().createSection("batiments"); // ancienne config : tout à 0

        levels.clear();
        ConfigurationSection lv = root.getConfigurationSection("niveaux");
        if (lv != null) {
            for (String k : lv.getKeys(false)) {
                try { levels.put(Integer.parseInt(k), lv.getInt(k)); } catch (NumberFormatException ignored) { }
            }
        }
        if (levels.isEmpty()) levels.putAll(Map.of(2, 15, 3, 30, 4, 50, 5, 75));

        ConfigurationSection p = root.getConfigurationSection("points");
        points = new Points(
                p == null ? 1 : p.getInt("variete", 1), p == null ? 30 : p.getInt("variete-max", 30),
                p == null ? 1 : p.getInt("decoration", 1), p == null ? 40 : p.getInt("decoration-max", 40),
                p == null ? 4 : p.getInt("decoration-par-type", 4),
                p == null ? 5 : p.getInt("bonus", 5),
                p == null ? 1 : p.getInt("surface", 1), p == null ? 15 : p.getInt("surface-max", 15));
        storageBonus = root.getDouble("bonus-entrepot", 0.25);

        decorations.clear();
        for (String token : root.getStringList("decorations")) {
            Set<Material> mats = resolve(token);
            if (mats.isEmpty()) warn("décoration inconnue : " + token);
            decorations.addAll(mats);
        }

        ConfigurationSection common = root.getConfigurationSection("commun");
        int doors  = common == null ? 1 : common.getInt("porte", 1);
        int lights = common == null ? 2 : common.getInt("lumiere", 2);
        int roof   = common == null ? 60 : common.getInt("toit", 60);

        habitation  = spec("habitation", root.getConfigurationSection("habitation"), doors, lights, roof);
        mairie      = spec("mairie", root.getConfigurationSection("mairie"), doors, lights, roof);
        freeAtelier = new Spec("atelier", List.of(), List.of(), doors, lights, roof);
        divers      = new Spec("divers", List.of(), List.of(), 0, 0, 0); // ni exigence ni contrôle : niveau au détail

        ateliers.clear();
        ConfigurationSection at = root.getConfigurationSection("ateliers");
        if (at != null) {
            for (String key : at.getKeys(false)) {
                ateliers.put(key.toLowerCase(Locale.ROOT), spec(key, at.getConfigurationSection(key), doors, lights, roof));
            }
        }
    }

    /** Exigences d'une section (requis, bonus, et porte / lumiere / toit qui remplacent les communes). */
    private Spec spec(String key, ConfigurationSection s, int doors, int lights, int roof) {
        if (s == null) return new Spec(key, List.of(), List.of(), doors, lights, roof);
        return new Spec(key, needs(key, s.getMapList("requis"), true), needs(key, s.getMapList("bonus"), false),
                s.getInt("porte", doors), s.getInt("lumiere", lights), s.getInt("toit", roof));
    }

    private List<Need> needs(String owner, List<Map<?, ?>> list, boolean required) {
        List<Need> out = new ArrayList<>();
        for (Map<?, ?> m : list) {
            String block = String.valueOf(m.get("bloc"));
            Set<Material> mats = resolve(block);
            if (mats.isEmpty()) {
                warn(owner + " : bloc inconnu « " + block + " » ignoré");
                continue;
            }
            String name = m.get("nom") != null ? m.get("nom").toString() : block.toLowerCase(Locale.ROOT).replace('_', ' ');
            String plural = m.get("pluriel") != null ? m.get("pluriel").toString() : name + "s";
            int count = required && m.get("nombre") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            out.add(new Need(name, plural, Collections.unmodifiableSet(mats), count));
        }
        return List.copyOf(out);
    }

    /**
     * « ANVIL », « #LOGS » (groupe du plugin), « #beds » ou « #minecraft:beds » (tag de blocs),
     * plusieurs séparés par des virgules. Ensemble vide si rien n'est reconnu.
     */
    public static Set<Material> resolve(String tokens) {
        // HashSet plutôt qu'EnumSet : sur un serveur hybride, les blocs de mods s'ajoutent à Material
        Set<Material> out = new HashSet<>();
        for (String raw : tokens.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            if (token.startsWith("#")) {
                String name = token.substring(1);
                Set<Material> group = name.contains(":") ? null : MaterialGroups.get(name);
                if (group != null) { out.addAll(group); continue; }
                NamespacedKey key = name.contains(":") ? NamespacedKey.fromString(name.toLowerCase(Locale.ROOT))
                        : NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
                Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
                if (tag != null) out.addAll(tag.getValues());
            } else {
                Material mat = Material.matchMaterial(token);
                if (mat != null) out.add(mat);
            }
        }
        return out;
    }

    private void warn(String message) {
        plugin.getLogger().warning("[Bâtiments] " + message);
    }

    /* =========================
       ACCÈS
       ========================= */

    /** Exigences d'un bâtiment selon son type (atelier : celles de l'artisan à qui il est réservé). */
    public Spec specFor(Building b) {
        return switch (b.type()) {
            case HABITATION -> habitation;
            case MAIRIE     -> mairie;
            case DIVERS     -> divers;
            case ATELIER    -> b.reservedNpc() != null ? specFor(b.reservedNpc()) : freeAtelier;
        };
    }

    /** Exigences d'un type de bâtiment (atelier : un atelier libre, sans artisan choisi). */
    public Spec specFor(BuildingType type) {
        return switch (type) {
            case HABITATION -> habitation;
            case MAIRIE     -> mairie;
            case DIVERS     -> divers;
            case ATELIER    -> freeAtelier;
        };
    }

    /** Exigences de l'atelier d'un artisan (communes seulement s'il n'a pas de section). */
    public Spec specFor(CityNPC npc) {
        Spec s = ateliers.get(npc.skinId.toLowerCase(Locale.ROOT));
        return s != null ? s : new Spec(npc.skinId, List.of(), List.of(),
                freeAtelier.doors(), freeAtelier.lights(), freeAtelier.roofPercent());
    }

    public NavigableMap<Integer, Integer> levels() { return levels; }
    public Points points()                         { return points; }
    public Set<Material> decorations()             { return decorations; }

    /** Capacité de l'entrepôt d'un artisan selon le niveau de son atelier : +bonus par niveau au-delà du 1er. */
    public double storageMultiplier(int level) {
        return 1.0 + storageBonus * Math.max(0, level - 1);
    }

    public double storageBonus() { return storageBonus; }

    /** « 1 enclume, 1 haut-fourneau et 1 table de forgeron » (blocs requis seulement), ou "". */
    public static String sentence(List<Need> needs) {
        List<String> parts = needs.stream().map(Need::label).toList();
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " et " + parts.get(parts.size() - 1);
    }

    /** Exigences générales lisibles : « une porte, un toit (60 % du sol) et 2 sources de lumière ». */
    public static String generalSentence(Spec spec) {
        List<String> parts = new ArrayList<>();
        if (spec.doors() > 0) parts.add(spec.doors() == 1 ? "une porte" : spec.doors() + " portes");
        if (spec.roofPercent() > 0) parts.add("un toit (" + spec.roofPercent() + " % du sol couvert)");
        if (spec.lights() > 0) parts.add(spec.lights() == 1 ? "une source de lumière" : spec.lights() + " sources de lumière");
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " et " + parts.get(parts.size() - 1);
    }

    /** Bulle : ce que demande un bâtiment (blocs requis, puis porte / toit / lumière, puis bonus). */
    public static List<String> lore(Spec spec) {
        List<String> lines = new ArrayList<>();
        for (Need n : spec.required()) lines.add("§7• §f" + n.label());
        if (spec.doors() > 0) lines.add("§7• §fune porte");
        if (spec.roofPercent() > 0) lines.add("§7• §fun toit §8(" + spec.roofPercent() + " % du sol couvert)");
        if (spec.lights() > 0) lines.add("§7• §f" + spec.lights() + " source" + (spec.lights() > 1 ? "s" : "") + " de lumière");
        if (!spec.bonus().isEmpty()) {
            lines.add("§8Bonus du métier : " + String.join(", ", spec.bonus().stream().map(Need::name).toList()));
        }
        return lines;
    }
}
