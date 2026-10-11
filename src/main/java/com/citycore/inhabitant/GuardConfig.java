package com.citycore.inhabitant;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Réglages de la garde (phase 5 du plan « Valmont vivante »), section {@code soldats} de
 * config.yml : taille de la garde et formations simultanées selon le rang de Roland, rôles
 * (soldat, archer, sergent…) avec leur équipement, leurs points de vie et leur durée de formation,
 * vitesse de patrouille et distance de vue.
 */
public final class GuardConfig {

    /** Un rôle de la garde. combat : true = à distance (arc), false = au contact. */
    public record Role(String key, String name, int rank, long trainingMs, double health, boolean ranged,
                       Material weapon, Material helmet, Material chest, Material legs, Material boots) {}

    private final JavaPlugin plugin;
    private final Map<Integer, Integer> maxGuards = new LinkedHashMap<>();
    private final Map<Integer, Integer> maxTraining = new LinkedHashMap<>();
    private final Map<String, Role> roles = new LinkedHashMap<>();
    private int   maxVisible = 50;   // gardes affichés à la fois
    private float patrolSpeed = 0.6f;
    private double sight = 16;
    private double bowRange = 14;

    public GuardConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Relit la section soldats directement dans config.yml. */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        ConfigurationSection root = file.exists() ? YamlConfiguration.loadConfiguration(file) : plugin.getConfig();
        ConfigurationSection s = root.getConfigurationSection("soldats");
        if (s == null) s = root.createSection("soldats");

        readLevels(s.getConfigurationSection("max"), maxGuards, Map.of(1, 8, 2, 16, 3, 25, 4, 36, 5, 50));
        readLevels(s.getConfigurationSection("formations"), maxTraining, Map.of(1, 2, 2, 3, 3, 4, 4, 5, 5, 6));
        maxVisible  = Math.max(0, s.getInt("max-visibles", 50));
        patrolSpeed = (float) Math.max(0.2, Math.min(1.5, s.getDouble("vitesse-patrouille", 0.6)));
        sight       = Math.max(4, s.getDouble("vision", 16));
        bowRange    = Math.max(4, s.getDouble("portee-arc", 14));

        roles.clear();
        ConfigurationSection r = s.getConfigurationSection("roles");
        if (r != null) {
            for (String key : r.getKeys(false)) {
                ConfigurationSection c = r.getConfigurationSection(key);
                if (c == null) continue;
                roles.put(key, new Role(key, c.getString("nom", key), Math.max(1, c.getInt("rang", 1)),
                        Math.round(Math.max(0, c.getDouble("minutes", 10)) * 60_000),
                        Math.max(1, c.getDouble("vie", 20)), "distance".equalsIgnoreCase(c.getString("combat", "melee")),
                        mat(c.getString("arme"), Material.IRON_SWORD), mat(c.getString("casque"), null),
                        mat(c.getString("plastron"), null), mat(c.getString("jambieres"), null),
                        mat(c.getString("bottes"), null)));
            }
        }
        if (roles.isEmpty()) { // config sans rôles : un soldat par défaut
            roles.put("soldat", new Role("soldat", "Soldat", 1, 10 * 60_000L, 30, false, Material.IRON_SWORD,
                    Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS));
        }
    }

    private static void readLevels(ConfigurationSection s, Map<Integer, Integer> into, Map<Integer, Integer> defaults) {
        into.clear();
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try { into.put(Integer.parseInt(k), Math.max(0, s.getInt(k))); } catch (NumberFormatException ignored) { }
            }
        }
        if (into.isEmpty()) into.putAll(defaults);
    }

    private Material mat(String name, Material def) {
        if (name == null || name.isBlank()) return def;
        Material m = Material.matchMaterial(name);
        if (m == null) plugin.getLogger().warning("[Garde] objet inconnu : " + name);
        return m != null ? m : def;
    }

    /** Valeur pour un rang : celle du plus haut palier atteint (0 sous le premier). */
    private static int forRank(Map<Integer, Integer> table, int rank) {
        int value = 0, best = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> e : table.entrySet()) {
            if (e.getKey() <= rank && e.getKey() > best) { best = e.getKey(); value = e.getValue(); }
        }
        return value;
    }

    /** Taille maximale de la garde (soldats + recrues) au rang de Roland donné. */
    public int maxGuards(int rank)   { return forRank(maxGuards, rank); }

    /** Recrues formées en même temps au rang de Roland donné. */
    public int maxTraining(int rank) { return forRank(maxTraining, rank); }

    public List<Role> roles()        { return new ArrayList<>(roles.values()); }

    public Role role(String key)     { return key == null ? null : roles.get(key); }

    public int    maxVisible()       { return maxVisible; }
    public float  patrolSpeed()      { return patrolSpeed; }
    public double sight()            { return sight; }
    public double bowRange()         { return bowRange; }
}
