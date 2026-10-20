package com.citycore.siege;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Réglages des sièges (phase 4 du plan « Valmont vivante »), section {@code sieges} de config.yml :
 * palier minimal, rythme des vagues, nombre et type d'assaillants selon le palier de la ville et
 * les joueurs connectés, arrivée (durée, largeur du front, points d'entrée), escouades, champion,
 * récompense.
 */
public final class SiegeConfig {

    /** Ennemi nommé de la dernière vague (à partir de son palier). */
    public record Champion(int palier, EntityType type, String name, double health, Material weapon) {}

    private final JavaPlugin plugin;

    private int    firstTier = 4;
    private int    prepSeconds = 30;
    private int    pauseSeconds = 30;
    private int    durationMinutes = 20;
    private int    spawnDistance = 12;
    private int    arrivalSeconds = 15;   // arrivée d'une vague, étalée dans le temps
    private int    frontWidth = 40;       // largeur de la zone d'apparition
    private int    entryPoints = 4;       // points d'entrée sur cette largeur
    private int    squadMin = 4, squadMax = 8;
    private int    maxPerTarget = 3;      // assaillants au contact d'un même défenseur
    private boolean playersWounded = true;
    private final TreeMap<Integer, Integer> waves = new TreeMap<>();
    private double base = 4, perTier = 1, perPlayer = 0.5;
    private int    maxPerWave = 40;
    private final TreeMap<Integer, Map<EntityType, Integer>> monsters = new TreeMap<>();
    private Champion champion;
    private int    rewardCoins = 150, rewardCoinsPerTier = 50, rewardXp = 40, rewardXpPerTier = 10;

    public SiegeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Relit la section sieges directement dans config.yml. */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        ConfigurationSection root = file.exists() ? YamlConfiguration.loadConfiguration(file) : plugin.getConfig();
        ConfigurationSection s = root.getConfigurationSection("sieges");
        if (s == null) s = root.createSection("sieges");

        firstTier       = Math.max(1, s.getInt("premier-palier", 4));
        prepSeconds     = Math.max(0, s.getInt("preparation-secondes", 30));
        pauseSeconds    = Math.max(0, s.getInt("pause-vagues-secondes", 30));
        durationMinutes = Math.max(1, s.getInt("duree-minutes", 20));
        spawnDistance   = Math.max(4, Math.min(48, s.getInt("distance-apparition", 12)));
        arrivalSeconds  = Math.max(0, Math.min(120, s.getInt("arrivee-secondes", 15)));
        frontWidth      = Math.max(0, Math.min(96, s.getInt("largeur-front", 40)));
        entryPoints     = Math.max(1, Math.min(8, s.getInt("points-entree", 4)));
        ConfigurationSection sq = s.getConfigurationSection("escouades");
        squadMin        = sq == null ? 4 : Math.max(1, Math.min(16, sq.getInt("min", 4)));
        squadMax        = sq == null ? 8 : Math.max(squadMin, Math.min(16, sq.getInt("max", 8)));
        maxPerTarget    = sq == null ? 3 : Math.max(0, sq.getInt("max-par-cible", 3));
        playersWounded  = s.getBoolean("joueurs-blesses", true);

        waves.clear();
        ConfigurationSection w = s.getConfigurationSection("vagues");
        if (w != null) {
            for (String k : w.getKeys(false)) {
                try { waves.put(Integer.parseInt(k), Math.max(1, w.getInt(k))); } catch (NumberFormatException ignored) { }
            }
        }
        if (waves.isEmpty()) waves.putAll(Map.of(1, 2, 4, 3, 7, 4, 9, 5));

        ConfigurationSection a = s.getConfigurationSection("assaillants");
        base       = a == null ? 4 : Math.max(1, a.getDouble("base", 4));
        perTier    = a == null ? 1 : Math.max(0, a.getDouble("par-palier", 1));
        perPlayer  = a == null ? 0.5 : Math.max(0, a.getDouble("par-joueur", 0.5));
        maxPerWave = a == null ? 40 : Math.max(1, a.getInt("max", 40));

        monsters.clear();
        ConfigurationSection m = s.getConfigurationSection("monstres");
        if (m != null) {
            for (String k : m.getKeys(false)) {
                ConfigurationSection tier = m.getConfigurationSection(k);
                if (tier == null) continue;
                Map<EntityType, Integer> weights = new LinkedHashMap<>();
                for (String type : tier.getKeys(false)) {
                    EntityType t = entityType(type);
                    if (t == null) {
                        plugin.getLogger().warning("[Sièges] monstre inconnu ou non hostile : " + type);
                        continue;
                    }
                    weights.put(t, Math.max(0, tier.getInt(type)));
                }
                try { if (!weights.isEmpty()) monsters.put(Integer.parseInt(k), weights); } catch (NumberFormatException ignored) { }
            }
        }
        if (monsters.isEmpty()) monsters.put(1, Map.of(EntityType.ZOMBIE, 3, EntityType.SKELETON, 2));

        ConfigurationSection c = s.getConfigurationSection("champion");
        champion = null;
        if (c != null) {
            EntityType t = entityType(c.getString("type", "VINDICATOR"));
            Material weapon = c.getString("arme") == null ? null : Material.matchMaterial(c.getString("arme"));
            if (t != null) champion = new Champion(Math.max(1, c.getInt("palier", 9)), t,
                    c.getString("nom", "Le Champion"), Math.max(1, c.getDouble("vie", 120)), weapon);
        }

        readArmy(s);

        ConfigurationSection r = s.getConfigurationSection("recompense");
        if (r != null) {
            rewardCoins        = Math.max(0, r.getInt("coins", 150));
            rewardCoinsPerTier = Math.max(0, r.getInt("coins-par-palier", 50));
            rewardXp           = Math.max(0, r.getInt("xp-roland", 40));
            rewardXpPerTier    = Math.max(0, r.getInt("xp-roland-par-palier", 10));
        }
    }

    /** Type de créature hostile capable de se battre seule (ZOMBIE, PILLAGER…), ou null. */
    private static EntityType entityType(String name) {
        try {
            EntityType t = EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
            Class<?> cls = t.getEntityClass();
            return cls != null && org.bukkit.entity.Mob.class.isAssignableFrom(cls)
                    && org.bukkit.entity.Enemy.class.isAssignableFrom(cls) ? t : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Valeur du plus haut palier atteint d'une table {palier: valeur}. */
    private static <V> V forTier(TreeMap<Integer, V> table, int tier) {
        Map.Entry<Integer, V> e = table.floorEntry(tier);
        return e != null ? e.getValue() : table.firstEntry().getValue();
    }

    public int firstTier()       { return firstTier; }
    public int prepSeconds()     { return prepSeconds; }
    public int pauseSeconds()    { return pauseSeconds; }
    public long durationMs()     { return durationMinutes * 60_000L; }
    public int spawnDistance()   { return spawnDistance; }

    /** Durée (ms) sur laquelle s'étale l'arrivée d'une vague, escouade après escouade (0 = d'un coup). */
    public long arrivalMs()      { return arrivalSeconds * 1000L; }

    /** Largeur (blocs) de la zone d'apparition, le long de la lisière attaquée. */
    public int frontWidth()      { return frontWidth; }

    /** Points d'entrée répartis sur la largeur du front. */
    public int entryPoints()     { return entryPoints; }

    /** Taille d'une escouade d'assaillants (meneur compris). */
    public int squadMin()        { return squadMin; }
    public int squadMax()        { return squadMax; }

    /** Assaillants au contact d'un même défenseur au plus (0 = sans limite). */
    public int maxPerTarget()    { return maxPerTarget; }

    /** Pendant un siège, les assaillants peuvent blesser les joueurs, même en ville. */
    public boolean playersWounded() { return playersWounded; }

    /** Nombre de vagues au palier donné. */
    public int waves(int tier)   { return forTier(waves, tier); }

    /** Assaillants d'une vague : (base + par-palier × palier) × (1 + par-joueur × (joueurs - 1)), plafonné. */
    public int attackers(int tier, int players) {
        double n = (base + perTier * tier) * (1 + perPlayer * Math.max(0, players - 1));
        return (int) Math.max(1, Math.min(maxPerWave, Math.round(n)));
    }

    /** Un type d'assaillant tiré au hasard selon les poids du palier. */
    public EntityType randomMonster(int tier) {
        Map<EntityType, Integer> weights = forTier(monsters, tier);
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return EntityType.ZOMBIE;
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (Map.Entry<EntityType, Integer> e : weights.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return EntityType.ZOMBIE;
    }

    /** Champion de ce palier, ou null (palier trop bas, ou aucun champion configuré). */
    public Champion champion(int tier) {
        return champion != null && tier >= champion.palier() ? champion : null;
    }

    public int rewardCoins(int tier) { return rewardCoins + rewardCoinsPerTier * tier; }
    public int rewardXp(int tier)    { return rewardXp + rewardXpPerTier * tier; }

    /* =========================
       TYPES DE SIÈGE ET ARMÉE ENNEMIE (« bannière »)
       ========================= */

    /** Type de siège : vagues de monstres, ou armée d'une faction ennemie (soldats PNJ). */
    public enum Kind {
        MONSTRES("monstres"), BANNIERE("banniere");

        public final String key;

        Kind(String key) { this.key = key; }

        public static Kind parse(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(Locale.ROOT).replace("è", "e");
            for (Kind kind : values()) if (kind.key.equals(k)) return kind;
            return k.startsWith("monst") ? MONSTRES : k.startsWith("ban") || k.startsWith("arm") ? BANNIERE : null;
        }
    }

    /** Faction ennemie : nom (« l'Ost du Corbeau »), couleur du nom et de sa bannière. */
    public record Faction(String name, String color, Material banner) {}

    /** Unité d'une armée ennemie (comme un rôle de la garde). */
    public record Unit(String key, String name, double health, boolean ranged,
                       Material weapon, Material helmet, Material chest, Material legs, Material boots) {}

    private final Map<Kind, Integer> kinds = new LinkedHashMap<>();
    private final List<Faction> factions = new ArrayList<>();
    private final Map<String, Unit> units = new LinkedHashMap<>();
    private final TreeMap<Integer, Map<String, Integer>> composition = new TreeMap<>();
    private Unit armyChampion;
    private int armyChampionTier = 7;
    private float armySpeed = 1.0f;

    /** Couleurs reconnues : code de couleur du chat et bannière. */
    private static final Map<String, Object[]> COLORS = Map.ofEntries(
            Map.entry("noir", new Object[]{"§8", Material.BLACK_BANNER}),
            Map.entry("rouge", new Object[]{"§c", Material.RED_BANNER}),
            Map.entry("vert", new Object[]{"§2", Material.GREEN_BANNER}),
            Map.entry("bleu", new Object[]{"§9", Material.BLUE_BANNER}),
            Map.entry("jaune", new Object[]{"§e", Material.YELLOW_BANNER}),
            Map.entry("violet", new Object[]{"§5", Material.PURPLE_BANNER}),
            Map.entry("orange", new Object[]{"§6", Material.ORANGE_BANNER}),
            Map.entry("blanc", new Object[]{"§f", Material.WHITE_BANNER}),
            Map.entry("gris", new Object[]{"§7", Material.GRAY_BANNER}),
            Map.entry("marron", new Object[]{"§6", Material.BROWN_BANNER}),
            Map.entry("cyan", new Object[]{"§3", Material.CYAN_BANNER}));

    /** Lit types et banniere (appelé par reload). */
    private void readArmy(ConfigurationSection s) {
        kinds.clear();
        ConfigurationSection t = s.getConfigurationSection("types");
        if (t != null) {
            for (String k : t.getKeys(false)) {
                Kind kind = Kind.parse(k);
                if (kind != null) kinds.put(kind, Math.max(0, t.getInt(k)));
            }
        }
        if (kinds.values().stream().mapToInt(Integer::intValue).sum() <= 0) kinds.put(Kind.MONSTRES, 1);

        ConfigurationSection b = s.getConfigurationSection("banniere");
        factions.clear();
        units.clear();
        composition.clear();
        armyChampion = null;
        if (b == null) b = s.createSection("banniere");
        armySpeed = (float) Math.max(0.4, Math.min(1.6, b.getDouble("vitesse", 1.0)));
        for (Map<?, ?> f : b.getMapList("factions")) {
            Object[] color = COLORS.getOrDefault(String.valueOf(f.get("couleur")).trim().toLowerCase(Locale.ROOT), COLORS.get("rouge"));
            String name = f.get("nom") == null ? "une armée ennemie" : String.valueOf(f.get("nom"));
            factions.add(new Faction(name, (String) color[0], (Material) color[1]));
        }
        if (factions.isEmpty()) factions.add(new Faction("une armée ennemie", "§c", Material.RED_BANNER));

        ConfigurationSection u = b.getConfigurationSection("unites");
        if (u != null) {
            for (String key : u.getKeys(false)) {
                ConfigurationSection c = u.getConfigurationSection(key);
                if (c != null) units.put(key, unit(key, c));
            }
        }
        if (units.isEmpty()) {
            units.put("soldat", new Unit("soldat", "Soldat", 24, false, Material.IRON_SWORD,
                    Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, null, null));
        }
        ConfigurationSection comp = b.getConfigurationSection("composition");
        if (comp != null) {
            for (String tier : comp.getKeys(false)) {
                ConfigurationSection c = comp.getConfigurationSection(tier);
                if (c == null) continue;
                Map<String, Integer> weights = new LinkedHashMap<>();
                for (String key : c.getKeys(false)) {
                    if (units.containsKey(key)) weights.put(key, Math.max(0, c.getInt(key)));
                    else plugin.getLogger().warning("[Sièges] unité inconnue dans banniere.composition : " + key);
                }
                try { if (!weights.isEmpty()) composition.put(Integer.parseInt(tier), weights); } catch (NumberFormatException ignored) { }
            }
        }
        if (composition.isEmpty()) {
            Map<String, Integer> all = new LinkedHashMap<>();
            for (String key : units.keySet()) all.put(key, 1);
            composition.put(1, all);
        }
        ConfigurationSection ch = b.getConfigurationSection("champion");
        if (ch != null) {
            armyChampion = unit("champion", ch);
            armyChampionTier = Math.max(1, ch.getInt("palier", 7));
        }
    }

    private Unit unit(String key, ConfigurationSection c) {
        return new Unit(key, c.getString("nom", key), Math.max(1, c.getDouble("vie", 20)),
                "distance".equalsIgnoreCase(c.getString("combat", "melee")),
                mat(c.getString("arme"), Material.IRON_SWORD), mat(c.getString("casque"), null),
                mat(c.getString("plastron"), null), mat(c.getString("jambieres"), null), mat(c.getString("bottes"), null));
    }

    private Material mat(String name, Material def) {
        if (name == null || name.isBlank()) return def;
        Material m = Material.matchMaterial(name);
        if (m == null) plugin.getLogger().warning("[Sièges] objet inconnu : " + name);
        return m != null ? m : def;
    }

    /** Type d'un siège tiré au hasard selon les poids de sieges.types. */
    public Kind randomKind() {
        int total = kinds.values().stream().mapToInt(Integer::intValue).sum();
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        for (Map.Entry<Kind, Integer> e : kinds.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return Kind.MONSTRES;
    }

    public Faction randomFaction() {
        return factions.get(ThreadLocalRandom.current().nextInt(factions.size()));
    }

    /** Une unité tirée au hasard selon la composition du palier. */
    public Unit randomUnit(int tier) {
        Map<String, Integer> weights = forTier(composition, tier);
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total > 0) {
            int roll = ThreadLocalRandom.current().nextInt(total);
            for (Map.Entry<String, Integer> e : weights.entrySet()) {
                roll -= e.getValue();
                if (roll < 0) return units.get(e.getKey());
            }
        }
        return units.values().iterator().next();
    }

    /** Champion de l'armée ennemie à ce palier, ou null. */
    public Unit armyChampion(int tier) {
        return armyChampion != null && tier >= armyChampionTier ? armyChampion : null;
    }

    public float armySpeed() { return armySpeed; }
}
