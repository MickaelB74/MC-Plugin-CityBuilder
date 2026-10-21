package com.citycore.raid;

import com.citycore.integration.ItemIds;
import com.citycore.integration.ModIntegrations;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catalogue des raids (raids.yml, PLAN_RAIDS §7 et §8.5) et contrôle de chaque cible au
 * démarrage (§3.2) : mods présents (réglages de mod-integrations compris), structures et
 * créatures enregistrées sur le serveur, monde du Nether / de l'End activé. Une cible incomplète
 * est masquée, sans erreur ; la console reçoit une ligne de bilan.
 *
 * raids.yml est copié au premier démarrage, puis complété à chaque nouvelle version du jar : les
 * cibles, villes et structures écartées absentes du fichier y sont ajoutées, le reste est gardé.
 */
public final class RaidCatalog {

    private static final String FILE = "raids.yml";

    /** Objectifs connus (§5.2). */
    public static final Set<String> OBJECTIVES = Set.of(
            "garnison", "cloche", "bannieres", "porte", "chef", "maitre", "cages", "tenir", "liberer", "aborder", "tours");
    /** Bonus connus (§5.2). */
    public static final Set<String> BONUSES = Set.of("epargner", "sans-perte", "eclair");

    /** Raison d'une cible masquée : groupe (pour le bilan) et détail. */
    public record Hidden(String group, String detail) {}

    private final JavaPlugin plugin;
    private final RaidConfig config;

    private final Map<String, RaidTarget> targets = new LinkedHashMap<>();
    private final List<String> ignored = new ArrayList<>();
    private final List<Pattern> ignoredPatterns = new ArrayList<>();
    /** Cibles masquées et pourquoi. */
    private final Map<String, Hidden> hidden = new LinkedHashMap<>();
    /** Mod présent dont une structure attendue manque (mod mis à jour ?) : structures nommées. */
    private final Set<String> missingWithMod = new TreeSet<>();
    /** Erreurs de lecture de raids.yml (cible ignorée). */
    private final List<String> loadWarnings = new ArrayList<>();
    public RaidCatalog(JavaPlugin plugin, RaidConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /* =========================
       FICHIER : COPIE ET FUSION PAR VERSION
       ========================= */

    /** Copie raids.yml au premier démarrage ; complète un fichier d'une version plus ancienne. */
    public void ensureFile() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
            return;
        }
        String jarText;
        try (InputStream in = plugin.getResource(FILE)) {
            if (in == null) return;
            jarText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        YamlConfiguration jar = new YamlConfiguration();
        YamlConfiguration user = new YamlConfiguration();
        try {
            jar.loadFromString(jarText);
            user.load(file);
        } catch (Exception e) {
            plugin.getLogger().severe("⚔ [Raids] raids.yml contient une erreur (" + String.valueOf(e.getMessage()).lines()
                    .findFirst().orElse("") + ") — mise à jour automatique annulée.");
            return;
        }
        int jarVersion = jar.getInt("version", 1), fileVersion = user.getInt("version", 0);
        if (fileVersion >= jarVersion) return;
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File backup = new File(plugin.getDataFolder(), "raids-ancien-v" + fileVersion + "-" + stamp + ".yml");
            Files.copy(file.toPath(), backup.toPath());
            List<String> added = new ArrayList<>();
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String[] jarLines = jarText.split("\r?\n", -1);
            text = addMissing(text, jarLines, user, "cibles", jarVersion, added);
            text = addMissing(text, jarLines, user, "villes", jarVersion, added);
            text = addMissingIgnored(text, jarLines, user, added);
            text = text.replaceFirst("(?m)^version:\\s*\\d+", "version: " + jarVersion);
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            plugin.getLogger().info("⚔ [Raids] raids.yml mis à jour (version " + fileVersion + " → " + jarVersion + ") : "
                    + (added.isEmpty() ? "rien à ajouter" : added.size() + " entrée(s) ajoutée(s)")
                    + ", vos réglages sont conservés (copie : " + backup.getName() + ").");
        } catch (IOException e) {
            plugin.getLogger().severe("⚔ [Raids] Impossible de mettre à jour raids.yml : " + e.getMessage());
        }
    }

    /** Entrées d'une section (une par ligne) que le jar a et pas le fichier, ajoutées sous son en-tête. */
    static String addMissing(String text, String[] jarLines, YamlConfiguration user, String section,
                                     int version, List<String> added) {
        Pattern entry = Pattern.compile("^  ([a-z0-9-]+):");
        List<String> missing = new ArrayList<>();
        boolean inside = false;
        for (String line : jarLines) {
            if (line.startsWith(section + ":")) { inside = true; continue; }
            if (!inside) continue;
            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")) break;
            Matcher m = entry.matcher(line);
            if (m.find() && !user.contains(section + "." + m.group(1))) {
                missing.add(line);
                added.add(section + "." + m.group(1));
            }
        }
        if (missing.isEmpty()) return text;
        Matcher header = Pattern.compile("(?m)^" + section + ":[^\\n]*\\n").matcher(text);
        if (!header.find()) return text.stripTrailing() + "\n" + section + ":\n  # ajoutées en version " + version + "\n"
                + String.join("\n", missing) + "\n";
        return text.substring(0, header.end()) + "  # ajoutées en version " + version + "\n"
                + String.join("\n", missing) + "\n" + text.substring(header.end());
    }

    static String addMissingIgnored(String text, String[] jarLines, YamlConfiguration user, List<String> added) {
        Set<String> have = new LinkedHashSet<>(user.getStringList("ignorees"));
        Pattern item = Pattern.compile("^  - \"?([^\"#]+?)\"?\\s*$");
        List<String> missing = new ArrayList<>();
        boolean inside = false;
        for (String line : jarLines) {
            if (line.startsWith("ignorees:")) { inside = true; continue; }
            if (!inside) continue;
            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")) break;
            Matcher m = item.matcher(line);
            if (m.find() && !have.contains(m.group(1))) {
                missing.add(line);
                added.add("ignorees." + m.group(1));
            }
        }
        if (missing.isEmpty()) return text;
        Matcher header = Pattern.compile("(?m)^ignorees:[^\\n]*\\n").matcher(text);
        if (!header.find()) return text.stripTrailing() + "\nignorees:\n" + String.join("\n", missing) + "\n";
        return text.substring(0, header.end()) + String.join("\n", missing) + "\n" + text.substring(header.end());
    }

    /* =========================
       LECTURE
       ========================= */

    /** Lit raids.yml (cibles, villes, structures écartées). */
    public void load() {
        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.load(new File(plugin.getDataFolder(), FILE));
        } catch (Exception e) {
            targets.clear();
            loadWarnings.clear();
            loadWarnings.add("raids.yml illisible : " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            plugin.getLogger().warning("⚔ [Raids] " + loadWarnings.get(0));
            return;
        }
        load(yml);
        for (String w : loadWarnings) plugin.getLogger().warning("⚔ [Raids] raids.yml : " + w);
    }

    /** Lecture du contenu (sans accès au serveur : contrôlable hors jeu). */
    void load(YamlConfiguration yml) {
        targets.clear();
        ignored.clear();
        ignoredPatterns.clear();
        loadWarnings.clear();
        ConfigurationSection cibles = yml.getConfigurationSection("cibles");
        if (cibles != null) {
            for (String key : cibles.getKeys(false)) {
                ConfigurationSection c = cibles.getConfigurationSection(key);
                RaidTarget t = c == null ? null : parseTarget(key, c);
                if (t != null) targets.put(key, t);
            }
        }
        ConfigurationSection villes = yml.getConfigurationSection("villes");
        if (villes != null) {
            for (String key : villes.getKeys(false)) {
                ConfigurationSection c = villes.getConfigurationSection(key);
                RaidTarget t = c == null ? null : parseCity(key, c);
                if (t == null) continue;
                if (targets.containsKey(key)) {
                    loadWarnings.add("ville " + key + " : même clé qu'une cible, ignorée");
                    continue;
                }
                targets.put(key, t);
            }
        }
        for (String pattern : yml.getStringList("ignorees")) {
            String p = pattern.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            ignored.add(p);
            ignoredPatterns.add(glob(p));
        }
    }

    private RaidTarget parseTarget(String key, ConfigurationSection c) {
        RaidTarget.Type type = RaidTarget.Type.parse(c.getString("type"));
        if (type == null) {
            loadWarnings.add(key + " : type « " + c.getString("type") + " » inconnu, cible ignorée");
            return null;
        }
        int level = c.getInt("niveau", 0);
        if (level < 1 || level > 7) {
            loadWarnings.add(key + " : niveau " + level + " hors de 1 à 7, cible ignorée");
            return null;
        }
        List<RaidTarget.Piece> pieces = new ArrayList<>();
        if (c.isString("structure")) pieces.add(new RaidTarget.Piece(c.getString("structure").trim().toLowerCase(Locale.ROOT), "centre"));
        for (Map<?, ?> m : c.getMapList("structures")) {
            Object id = m.get("id");
            if (id == null) continue;
            Object role = m.get("role");
            pieces.add(new RaidTarget.Piece(id.toString().trim().toLowerCase(Locale.ROOT), role == null ? "centre" : role.toString()));
        }
        if (pieces.isEmpty()) {
            loadWarnings.add(key + " : aucune structure, cible ignorée");
            return null;
        }
        RaidTarget.Defenders defenders = parseDefenders(c.getConfigurationSection("defenseurs"));
        List<String> objectives = new ArrayList<>();
        List<String> bonuses = new ArrayList<>();
        parseObjectives(key, c.getList("objectifs"), objectives, bonuses);
        if (objectives.isEmpty()) objectives.addAll(defaultObjectives(type, level, defenders));
        addDefaultBonuses(type, bonuses);
        String size = c.getString("taille", "");
        List<String> terrain = terrain(key, c.getStringList("terrain"));
        Set<String> mods = mods(pieces, defenders, null, c.getString("avec-mod"));
        return new RaidTarget(key, c.getString("nom", key), type, level, terrain, pieces, size,
                c.getString("occupants", ""), defenders, objectives, bonuses, mods,
                c.getBoolean("souterrain", false), c.getBoolean("flottant", false),
                c.contains("heure") ? c.getLong("heure") : null, null, c.getBoolean("active", true));
    }

    /**
     * Ville fortifiée (refaite sans /place, 21/10/2026) : village naturel (village.naturels, sinon
     * ceux qui naissent sur les terrains de la ville), tours (tours.id, tours.nombre) et siège
     * (siege) posés par le plugin. Les anciennes clés village.pool, profondeur et structure ne
     * servent plus.
     */
    private RaidTarget parseCity(String key, ConfigurationSection c) {
        ConfigurationSection v = c.getConfigurationSection("village");
        ConfigurationSection towers = c.getConfigurationSection("tours");
        String seat = c.getString("siege");
        if (towers == null || seat == null || towers.getString("id") == null) {
            loadWarnings.add("ville " + key + " : tours (id) et siege requis, ville ignorée");
            return null;
        }
        List<String> terrain = terrain("ville " + key, c.getStringList("terrain"));
        List<String> villages = new ArrayList<>();
        if (v != null) for (String id : v.getStringList("naturels")) villages.add(id.trim().toLowerCase(Locale.ROOT));
        if (villages.isEmpty()) villages.addAll(naturalVillages(terrain));
        if (villages.isEmpty()) {
            loadWarnings.add("ville " + key + " : aucun village naturel pour ses terrains (village.naturels), ville ignorée");
            return null;
        }
        RaidTarget.City city = new RaidTarget.City(List.copyOf(villages), towers.getString("id").trim().toLowerCase(Locale.ROOT),
                Math.max(1, Math.min(3, towers.getInt("nombre", 3))), seat.trim().toLowerCase(Locale.ROOT));
        // Pièce du centre : le premier village présent sur le serveur (les autres sont des variantes)
        String centre = villages.stream().filter(ModIntegrations::structureExists).findFirst().orElse(villages.get(0));
        RaidTarget.Piece tower = new RaidTarget.Piece(city.tower(), "tour"), seatPiece = new RaidTarget.Piece(city.seat(), "siege");
        List<RaidTarget.Piece> pieces = List.of(new RaidTarget.Piece(centre, "centre"), tower, seatPiece);
        // Faction de la ville : soldats des sièges ; les villageois restent (bonus « épargner »)
        RaidTarget.Defenders defenders = new RaidTarget.Defenders("armee", true, List.of(), false, null, null);
        int level = Math.max(1, Math.min(7, c.getInt("niveau", 5)));
        List<String> bonuses = new ArrayList<>();
        addDefaultBonuses(RaidTarget.Type.VILLE, bonuses);
        return new RaidTarget(key, c.getString("nom", key), RaidTarget.Type.VILLE, level,
                terrain, pieces, "", c.getString("occupants", ""),
                defenders, defaultObjectives(RaidTarget.Type.VILLE, level, defenders), bonuses,
                mods(List.of(tower, seatPiece), defenders, null, c.getString("avec-mod")), false, false, null, city,
                c.getBoolean("active", true));
    }

    /** Villages qui naissent sur ces terrains (jeu de base, puis Towns and Towers). */
    private static List<String> naturalVillages(List<String> terrain) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String t : terrain) {
            switch (t) {
                case "plaines" -> out.addAll(List.of("minecraft:village_plains", "towns_and_towers:village_sunflower_plains"));
                case "prairie" -> out.add("towns_and_towers:village_meadow");
                case "taiga" -> out.addAll(List.of("minecraft:village_taiga", "towns_and_towers:village_old_growth_taiga"));
                case "neige" -> out.addAll(List.of("minecraft:village_snowy", "towns_and_towers:village_snowy_taiga",
                        "towns_and_towers:village_grove", "towns_and_towers:village_snowy_slopes"));
                case "savane" -> out.addAll(List.of("minecraft:village_savanna", "towns_and_towers:village_savanna_plateau"));
                case "plage" -> out.add("towns_and_towers:village_beach");
                case "marais" -> out.add("towns_and_towers:village_swamp");
                case "desert" -> out.add("minecraft:village_desert");
                case "foret" -> out.addAll(List.of("towns_and_towers:village_forest", "towns_and_towers:village_birch_forest",
                        "towns_and_towers:village_flower_forest"));
                case "jungle" -> out.addAll(List.of("towns_and_towers:village_jungle", "towns_and_towers:village_sparse_jungle"));
                case "badlands" -> out.addAll(List.of("towns_and_towers:village_badlands", "towns_and_towers:village_wooded_badlands"));
                default -> { }
            }
        }
        return new ArrayList<>(out);
    }

    private static RaidTarget.Defenders parseDefenders(ConfigurationSection d) {
        if (d == null) return new RaidTarget.Defenders("gabarit", false, List.of(), false, null, null);
        String source = d.getString("source", "gabarit").trim().toLowerCase(Locale.ROOT);
        List<String> creatures = d.getStringList("creatures").stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
        String master = d.getString("maitre");
        String chief = d.getString("chef");
        return new RaidTarget.Defenders(source, d.getBoolean("complement", false), creatures,
                d.getBoolean("remplacer", false),
                master == null ? null : master.trim().toLowerCase(Locale.ROOT),
                chief == null ? null : chief.trim().toLowerCase(Locale.ROOT));
    }

    /** « objectifs: [garnison, cloche, {bonus: epargner}] » */
    private void parseObjectives(String key, List<?> raw, List<String> objectives, List<String> bonuses) {
        if (raw == null) return;
        for (Object o : raw) {
            if (o instanceof Map<?, ?> m && m.get("bonus") != null) {
                String b = m.get("bonus").toString().trim().toLowerCase(Locale.ROOT);
                if (BONUSES.contains(b)) bonuses.add(b);
                else loadWarnings.add(key + " : bonus « " + b + " » inconnu");
            } else if (o != null) {
                String obj = o.toString().trim().toLowerCase(Locale.ROOT);
                if (OBJECTIVES.contains(obj)) objectives.add(obj);
                else loadWarnings.add(key + " : objectif « " + obj + " » inconnu");
            }
        }
    }

    /**
     * Objectifs d'une cible sans objectifs écrits (§4.2, §5.2, §5.4) : ceux de son type ; les cages
     * à partir du niveau IV (et pour les repaires), un chef à partir du niveau V (et pour les villes
     * et les navires). L'assaut retire ensuite ceux dont la cible n'a pas l'élément (pas de cloche,
     * pas de bannière).
     */
    static List<String> defaultObjectives(RaidTarget.Type type, int level, RaidTarget.Defenders defenders) {
        List<String> list = new ArrayList<>(switch (type) {
            case CAMP -> List.of("garnison");
            case AVANT_POSTE -> List.of("garnison", "bannieres", "liberer");
            case VILLAGE -> List.of("garnison", "cloche");
            case VILLE -> List.of("garnison", "tours", "bannieres", "chef", "tenir");
            case FORT -> List.of("garnison", "cages", "chef");
            case CHATEAU -> List.of("porte", "bannieres", "garnison", "chef", "tenir");
            case ABORDAGE -> List.of("aborder", "garnison", "bannieres", "chef");
            case REPAIRE -> List.of("maitre", "cages");
        });
        if (level < 4 && type != RaidTarget.Type.REPAIRE) list.remove("cages");
        boolean chiefAlways = type == RaidTarget.Type.VILLE || type == RaidTarget.Type.ABORDAGE || defenders.chief() != null;
        if (level < 5 && !chiefAlways) list.remove("chef");
        if (defenders.chief() != null && !list.contains("chef")) list.add("chef");
        return list;
    }

    private static void addDefaultBonuses(RaidTarget.Type type, List<String> bonuses) {
        if ((type == RaidTarget.Type.VILLAGE || type == RaidTarget.Type.VILLE) && !bonuses.contains("epargner")) {
            bonuses.add(0, "epargner");
        }
        for (String b : List.of("sans-perte", "eclair")) if (!bonuses.contains(b)) bonuses.add(b);
    }

    /**
     * Terrain où la structure naît (indicatif depuis la génération naturelle : le jeu choisit
     * lui-même le terrain ; seuls nether et end désignent un autre monde). Faute de frappe signalée.
     */
    private List<String> terrain(String key, List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String t : raw) {
            String name = t.trim().toLowerCase(Locale.ROOT);
            if (!TERRAINS.contains(name)) loadWarnings.add(key + " : terrain « " + name + " » inconnu");
            out.add(name);
        }
        return out;
    }

    /** Terrains écrits dans raids.yml (familles de biomes du plan, §2.4). */
    private static final Set<String> TERRAINS = Set.of("plaines", "prairie", "foret", "foret-sombre", "taiga", "neige",
            "sommets", "desert", "savane", "badlands", "jungle", "marais", "champignons", "plage", "mer", "mer-froide",
            RaidTarget.NETHER, RaidTarget.END, "variee");

    /**
     * Mods requis : ceux des espaces de noms des structures, du pool et des créatures nommées
     * (towns_and_towers et kaisyn → t_and_t), plus avec-mod.
     */
    private static Set<String> mods(List<RaidTarget.Piece> pieces, RaidTarget.Defenders d, String pool, String with) {
        Set<String> out = new LinkedHashSet<>();
        List<String> ids = new ArrayList<>();
        pieces.forEach(p -> ids.add(p.id()));
        if (pool != null) ids.add(pool);
        ids.addAll(d.creatures());
        if (d.master() != null) ids.add(d.master());
        if (d.chief() != null) ids.add(d.chief());
        for (String id : ids) {
            String ns = ItemIds.namespace(id);
            if (NamespacedKey.MINECRAFT.equals(ns)) continue;
            ModIntegrations.Mod mod = ModIntegrations.Mod.byId(ns);
            out.add(mod != null ? mod.id : ns);
        }
        if (with != null) {
            for (String m : with.split(",")) if (!m.isBlank()) out.add(m.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** « mostructures:*_abandoned_church » : « * » remplace n'importe quels caractères. */
    private static Pattern glob(String pattern) {
        StringBuilder sb = new StringBuilder();
        String[] parts = pattern.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(".*");
            sb.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(sb.toString());
    }

    /* =========================
       DISPONIBILITÉ (§3.2)
       ========================= */

    /**
     * Contrôles au démarrage : mods, structures, créatures, mondes du Nether et de l'End. Qu'un
     * exemplaire naisse bien dans le monde des raids ne se sait qu'à sa recherche
     * ({@link #markNotFound}).
     */
    public void checkStatic() {
        hidden.clear();
        missingWithMod.clear();
        for (RaidTarget t : targets.values()) {
            Hidden h = staticReason(t);
            if (h != null) hidden.put(t.key(), h);
        }
    }

    private Hidden staticReason(RaidTarget t) {
        if (!t.enabled()) return new Hidden("désactivées dans raids.yml", t.name());
        for (String mod : t.mods()) {
            if (!ModIntegrations.present(mod)) {
                ModIntegrations.Mod known = ModIntegrations.Mod.byId(mod);
                return new Hidden((known != null ? known.displayName : mod) + " absent", t.name());
            }
        }
        for (RaidTarget.Piece p : t.pieces()) {
            if (!ModIntegrations.structureExists(p.id())) {
                missingWithMod.add(p.id());
                return new Hidden("structure absente du serveur", t.name() + " (" + p.id() + ")");
            }
        }
        List<String> creatures = new ArrayList<>(t.defenders().creatures());
        if (t.defenders().master() != null) creatures.add(t.defenders().master());
        if (t.defenders().chief() != null) creatures.add(t.defenders().chief());
        for (String id : creatures) {
            if (ItemIds.resolveEntity(id) == null) return new Hidden("créature inconnue", t.name() + " (" + id + ")");
        }
        if (t.otherDimension() && t.terrain().contains(RaidTarget.NETHER) && !config.nether.active()) {
            return new Hidden("monde du Nether des raids désactivé", t.name());
        }
        if (t.otherDimension() && t.terrain().contains(RaidTarget.END) && !config.end.active()) {
            return new Hidden("monde de l'End des raids désactivé", t.name());
        }
        return null;
    }

    /**
     * La structure principale n'a aucun placement dans le monde des raids (aucun de ses biomes n'y
     * existe, voir {@link NaturalPlacements}) : elle n'y naîtra jamais, la cible est masquée.
     */
    public void markNeverGenerates(RaidTarget t) {
        if (t != null && !hidden.containsKey(t.key())) {
            hidden.put(t.key(), new Hidden("ne naissent pas sur ce serveur (biomes de mods absents)", t.name()));
        }
    }

    /**
     * Aucun exemplaire naturel trouvé dans le monde des raids (structure qui ne naît pas sur ce
     * serveur, réglages de génération, biome trop rare) : masquée jusqu'au prochain démarrage.
     */
    public void markNotFound(RaidTarget t) {
        if (t != null && !hidden.containsKey(t.key())) {
            hidden.put(t.key(), new Hidden("introuvable dans le monde des raids", t.name()));
        }
    }

    public boolean isAvailable(RaidTarget t) {
        return t != null && !hidden.containsKey(t.key());
    }

    /** Pourquoi la cible est masquée (null si elle est disponible). */
    public Hidden reason(RaidTarget t) {
        return t == null ? null : hidden.get(t.key());
    }

    /* =========================
       CONSULTATION
       ========================= */

    public List<RaidTarget> all() {
        return new ArrayList<>(targets.values());
    }

    public List<RaidTarget> byLevel(int level) {
        return targets.values().stream().filter(t -> t.level() == level).toList();
    }

    public RaidTarget get(String key) {
        return key == null ? null : targets.get(key.toLowerCase(Locale.ROOT));
    }

    /** Cible dont une pièce est cette structure (la première trouvée), ou null. */
    public RaidTarget byStructure(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (RaidTarget t : targets.values()) {
            for (RaidTarget.Piece p : t.pieces()) if (p.id().equals(s)) return t;
        }
        return null;
    }

    /** Identifiants de toutes les structures du catalogue (complétion de /city raid essai). */
    public Set<String> structureIds() {
        Set<String> out = new TreeSet<>();
        for (RaidTarget t : targets.values()) t.pieces().forEach(p -> out.add(p.id()));
        return out;
    }

    public long cityCount() {
        return targets.values().stream().filter(t -> t.city() != null).count();
    }

    public long availableCount() {
        return targets.values().stream().filter(this::isAvailable).count();
    }

    /**
     * Ligne de bilan : « ⚔ Raids : 168 cibles et 8 villes, 160 disponibles ; 16 masquées
     * (Supplementaries absent : Galion ; aucun emplacement « champignons » : …) ».
     */
    public String summary() {
        long cities = cityCount();
        long plain = targets.size() - cities;
        StringBuilder sb = new StringBuilder("⚔ Raids : " + plain + " cibles et " + cities + " villes, "
                + availableCount() + " disponibles");
        if (hidden.isEmpty()) return sb.toString();
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        for (Hidden h : hidden.values()) byGroup.computeIfAbsent(h.group(), k -> new ArrayList<>()).add(h.detail());
        sb.append(" ; ").append(hidden.size()).append(" masquées (");
        List<String> parts = new ArrayList<>();
        byGroup.forEach((group, names) -> parts.add(group + " : " + (names.size() <= 3 ? String.join(", ", names)
                : String.join(", ", names.subList(0, 3)) + " et " + (names.size() - 3) + " autres")));
        sb.append(String.join(" ; ", parts)).append(')');
        return sb.toString();
    }

    /** Avertissements : structure attendue absente alors que son mod est présent (mod mis à jour ?). */
    public List<String> modWarnings() {
        List<String> out = new ArrayList<>();
        for (String id : missingWithMod) {
            String ns = ItemIds.namespace(id);
            ModIntegrations.Mod mod = ModIntegrations.Mod.byId(ns);
            if (mod != null && ModIntegrations.present(mod.id)) {
                out.add(mod.displayName + " est présent mais la structure " + id + " n'existe pas (mod mis à jour ?)");
            }
        }
        return out;
    }

    /** « Raids : 58 cibles (58 disponibles) » pour un mod ; null = jeu de base. Vide si aucune cible. */
    public String modLine(String modId) {
        int total = 0, available = 0;
        for (RaidTarget t : targets.values()) {
            boolean concerned = modId == null ? t.mods().isEmpty() : t.mods().contains(modId);
            if (!concerned) continue;
            total++;
            if (isAvailable(t)) available++;
        }
        return total == 0 ? "" : "Raids : " + total + " cible" + (total > 1 ? "s" : "") + " (" + available + " disponible"
                + (available > 1 ? "s" : "") + ")";
    }

    /**
     * Structures enregistrées sur le serveur qui ne sont ni au catalogue ni écartées : à classer
     * quand un nouveau mod de structures arrive (§3.4).
     */
    public List<String> unknownStructures() {
        Set<String> known = structureIds();
        List<String> out = new ArrayList<>();
        try {
            for (org.bukkit.generator.structure.Structure s : Registry.STRUCTURE) {
                String id = s.getKey().toString();
                if (known.contains(id) || isIgnored(id)) continue;
                out.add(id);
            }
        } catch (RuntimeException | LinkageError e) {
            out.add("(registre des structures illisible : " + e.getMessage() + ")");
        }
        out.sort(null);
        return out;
    }

    public boolean isIgnored(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (Pattern p : ignoredPatterns) if (p.matcher(s).matches()) return true;
        return false;
    }

    public List<String> loadWarnings() { return loadWarnings; }
}
