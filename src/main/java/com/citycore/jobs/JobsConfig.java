package com.citycore.jobs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

/**
 * Réglages des emplois (section {@code emplois} de config.yml) : nombre d'employés par artisan
 * selon son rang, durée d'un cycle selon le niveau de l'annexe, quantité produite, valeur des
 * objets produits (les objets de valeur sont produits plus rarement, les trophées jamais),
 * plafond de remplissage des entrepôts.
 */
public final class JobsConfig {

    private final JavaPlugin plugin;
    private final TreeMap<Integer, Integer> maxEmployees = new TreeMap<>();
    private final TreeMap<Integer, Double> cycleMinutes = new TreeMap<>();
    private double perCycle = 8;
    private double fullValue = 0.5;
    private double maxValue = 20;
    private int ceiling = 50;
    private double speed = 1;

    public JobsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Relit la section emplois directement dans config.yml. */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        ConfigurationSection root = file.exists() ? YamlConfiguration.loadConfiguration(file) : plugin.getConfig();
        ConfigurationSection s = root.getConfigurationSection("emplois");
        if (s == null) s = root.createSection("emplois");

        maxEmployees.clear();
        ConfigurationSection m = s.getConfigurationSection("employes-max");
        if (m != null) {
            for (String k : m.getKeys(false)) {
                try { maxEmployees.put(Integer.parseInt(k), Math.max(0, m.getInt(k))); } catch (NumberFormatException ignored) { }
            }
        }
        if (maxEmployees.isEmpty()) maxEmployees.putAll(Map.of(1, 1, 2, 2, 3, 2, 4, 3, 5, 3));

        cycleMinutes.clear();
        ConfigurationSection c = s.getConfigurationSection("cycle-minutes");
        if (c != null) {
            for (String k : c.getKeys(false)) {
                try { cycleMinutes.put(Integer.parseInt(k), Math.max(0.5, c.getDouble(k))); } catch (NumberFormatException ignored) { }
            }
        }
        if (cycleMinutes.isEmpty()) cycleMinutes.putAll(Map.of(1, 4.0, 2, 3.5, 3, 3.0, 4, 2.5, 5, 2.0));

        perCycle  = Math.max(0, s.getDouble("par-cycle", 8));
        fullValue = Math.max(0.01, s.getDouble("valeur-pleine", 0.5));
        maxValue  = Math.max(fullValue, s.getDouble("valeur-max", 20));
        ceiling   = Math.max(0, Math.min(100, s.getInt("plafond", 50)));
        speed     = Math.max(0.1, s.getDouble("vitesse", 1));
    }

    /** Employés au plus pour un artisan de ce rang. */
    public int maxEmployees(int rank) {
        Map.Entry<Integer, Integer> e = maxEmployees.floorEntry(rank);
        return e != null ? e.getValue() : 0;
    }

    /** Durée d'un cycle (ms) pour une annexe de ce niveau (≥ 1), divisée par la vitesse globale. */
    public long cycleMs(int annexLevel) {
        Map.Entry<Integer, Double> e = cycleMinutes.floorEntry(Math.max(1, annexLevel));
        double minutes = e != null ? e.getValue() : 4;
        return Math.round(minutes * 60_000 / speed);
    }

    /** Objets de chaque sorte produits par employé et par cycle (pour un objet courant). */
    public double perCycle()   { return perCycle; }

    /** Valeur unitaire (coins) jusqu'à laquelle un objet est produit à chaque cycle ; au-delà, plus rarement. */
    public double fullValue()  { return fullValue; }

    /** Valeur unitaire au-delà de laquelle un objet n'est jamais produit (trophées, raretés). */
    public double maxValue()   { return maxValue; }

    /** % de la capacité de l'entrepôt au-delà duquel les employés ne produisent plus cet objet. */
    public int ceiling()       { return ceiling; }
}
