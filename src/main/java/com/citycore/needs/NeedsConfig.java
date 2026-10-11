package com.citycore.needs;

import com.citycore.npc.CityNPC;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Réglages des besoins des habitants (phase 6 du plan « Valmont vivante »), section
 * {@code besoins} de config.yml : poids de chaque besoin, rations et remèdes consommés à l'aube
 * dans les entrepôts des artisans, garde attendue, confort selon le niveau des maisons, effets de
 * la satisfaction (arrivées, impôt).
 */
public final class NeedsConfig {

    /** Les quatre besoins, dans l'ordre d'affichage. */
    public enum Need {
        NOURRITURE("Nourriture"), SECURITE("Sécurité"), SANTE("Santé"), CONFORT("Confort");

        public final String label;

        Need(String label) { this.label = label; }
    }

    private final JavaPlugin plugin;

    private final Map<Need, Integer> weights = new LinkedHashMap<>();
    private double rationsPerInhabitant = 4;
    private double foodAbstractConsumption = 3;
    private final Map<CityNPC, Map<Material, Double>> foods = new LinkedHashMap<>();
    private double carePerInhabitant = 1;
    private double careAbstractConsumption = 4;
    private final Map<CityNPC, Map<Material, Double>> remedies = new LinkedHashMap<>();
    private double inhabitantsPerGuard = 5;
    private int siegeWeight = 40;
    private int siegesKept = 3;
    private final TreeMap<Integer, Integer> comfort = new TreeMap<>();
    private final TreeMap<Integer, Double> arrival = new TreeMap<>();
    private final TreeMap<Integer, Double> tax = new TreeMap<>();

    public NeedsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Relit la section besoins directement dans config.yml. */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        ConfigurationSection root = file.exists() ? YamlConfiguration.loadConfiguration(file) : plugin.getConfig();
        ConfigurationSection s = root.getConfigurationSection("besoins");
        if (s == null) s = root.createSection("besoins");

        weights.clear();
        ConfigurationSection w = s.getConfigurationSection("poids");
        weights.put(Need.NOURRITURE, w == null ? 35 : Math.max(0, w.getInt("nourriture", 35)));
        weights.put(Need.SECURITE, w == null ? 25 : Math.max(0, w.getInt("securite", 25)));
        weights.put(Need.SANTE, w == null ? 15 : Math.max(0, w.getInt("sante", 15)));
        weights.put(Need.CONFORT, w == null ? 25 : Math.max(0, w.getInt("confort", 25)));

        ConfigurationSection f = s.getConfigurationSection("nourriture");
        rationsPerInhabitant    = f == null ? 4 : Math.max(0, f.getDouble("rations-par-habitant", 4));
        foodAbstractConsumption = f == null ? 3 : Math.max(0, f.getDouble("consommation-abstraite", 3));
        readStocks(f == null ? null : f.getConfigurationSection("aliments"), foods,
                Map.of(CityNPC.FARMER, Map.of(Material.WHEAT, 1.0, Material.CARROT, 1.0, Material.POTATO, 1.0),
                        CityNPC.INNKEEPER, Map.of(Material.BEEF, 3.0, Material.PORKCHOP, 3.0, Material.CHICKEN, 2.0)));

        ConfigurationSection h = s.getConfigurationSection("sante");
        carePerInhabitant       = h == null ? 1 : Math.max(0, h.getDouble("soins-par-habitant", 1));
        careAbstractConsumption = h == null ? 4 : Math.max(0, h.getDouble("consommation-abstraite", 4));
        readStocks(h == null ? null : h.getConfigurationSection("remedes"), remedies,
                Map.of(CityNPC.MONK, Map.of(Material.HONEY_BOTTLE, 2.0, Material.NETHER_WART, 1.0)));

        ConfigurationSection sec = s.getConfigurationSection("securite");
        inhabitantsPerGuard = sec == null ? 5 : Math.max(0.5, sec.getDouble("habitants-par-garde", 5));
        siegeWeight         = sec == null ? 40 : Math.max(0, Math.min(100, sec.getInt("poids-sieges", 40)));
        siegesKept          = sec == null ? 3 : Math.max(1, sec.getInt("sieges-retenus", 3));

        readLevels(s.getConfigurationSection("confort"), comfort, Map.of(0, 20, 1, 40, 2, 55, 3, 70, 4, 85, 5, 100));

        ConfigurationSection e = s.getConfigurationSection("effets");
        // La satisfaction est un bonus : au plus bas, arrivées 1,5 fois plus lentes et impôt × 0,75
        readFactors(e == null ? null : e.getConfigurationSection("arrivee"), arrival, Map.of(80, 0.6, 50, 1.0, 0, 1.5));
        readFactors(e == null ? null : e.getConfigurationSection("impot"), tax, Map.of(80, 1.5, 50, 1.0, 0, 0.75));
    }

    /** {cle_artisan: {MATERIAU: valeur}} → par artisan ; section absente ou vide : valeurs par défaut. */
    private void readStocks(ConfigurationSection s, Map<CityNPC, Map<Material, Double>> into,
                            Map<CityNPC, Map<Material, Double>> defaults) {
        into.clear();
        if (s != null) {
            for (String key : s.getKeys(false)) {
                CityNPC npc = artisan(key);
                ConfigurationSection items = s.getConfigurationSection(key);
                if (npc == null || items == null) {
                    plugin.getLogger().warning("[Besoins] artisan inconnu : " + key);
                    continue;
                }
                Map<Material, Double> values = new LinkedHashMap<>();
                for (String mat : items.getKeys(false)) {
                    Material m = Material.matchMaterial(mat);
                    if (m == null) plugin.getLogger().warning("[Besoins] objet inconnu : " + mat);
                    else values.put(m, Math.max(0, items.getDouble(mat)));
                }
                if (!values.isEmpty()) into.put(npc, values);
            }
        }
        if (into.isEmpty()) into.putAll(defaults);
    }

    /** Artisan d'après sa clé de config.yml (helene, rosalie, anselme…). */
    private static CityNPC artisan(String key) {
        for (CityNPC npc : CityNPC.values()) if (npc.skinId.equalsIgnoreCase(key)) return npc;
        return null;
    }

    private static void readLevels(ConfigurationSection s, TreeMap<Integer, Integer> into, Map<Integer, Integer> defaults) {
        into.clear();
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try { into.put(Integer.parseInt(k), Math.max(0, Math.min(100, s.getInt(k)))); } catch (NumberFormatException ignored) { }
            }
        }
        if (into.isEmpty()) into.putAll(defaults);
    }

    private static void readFactors(ConfigurationSection s, TreeMap<Integer, Double> into, Map<Integer, Double> defaults) {
        into.clear();
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try { into.put(Integer.parseInt(k), Math.max(0, s.getDouble(k))); } catch (NumberFormatException ignored) { }
            }
        }
        if (into.isEmpty()) into.putAll(defaults);
    }

    public int weight(Need need)                         { return weights.getOrDefault(need, 0); }
    public double rationsPerInhabitant()                  { return rationsPerInhabitant; }
    public double foodAbstractConsumption()               { return foodAbstractConsumption; }
    public Map<CityNPC, Map<Material, Double>> foods()    { return foods; }
    public double carePerInhabitant()                     { return carePerInhabitant; }
    public double careAbstractConsumption()               { return careAbstractConsumption; }
    public Map<CityNPC, Map<Material, Double>> remedies() { return remedies; }
    public double inhabitantsPerGuard()                   { return inhabitantsPerGuard; }
    public int siegeWeight()                              { return siegeWeight; }
    public int siegesKept()                               { return siegesKept; }

    /** Confort d'une maison selon son niveau (0 = incomplète). */
    public int comfort(int level) {
        Map.Entry<Integer, Integer> e = comfort.floorEntry(level);
        return e != null ? e.getValue() : comfort.firstEntry().getValue();
    }

    /** Multiplicateur du délai d'arrivée à cette satisfaction (0 = plus d'arrivée). */
    public double arrivalFactor(int satisfaction) {
        Map.Entry<Integer, Double> e = arrival.floorEntry(satisfaction);
        return e != null ? e.getValue() : 1.0;
    }

    /** Multiplicateur de l'impôt à cette satisfaction. */
    public double taxFactor(int satisfaction) {
        Map.Entry<Integer, Double> e = tax.floorEntry(satisfaction);
        return e != null ? e.getValue() : 1.0;
    }
}
