package com.citycore.npc.jacksparrow;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public class JackSparrowConfig {

    private final JavaPlugin plugin;
    private final Map<Material, Integer> prices = new LinkedHashMap<>();

    public JackSparrowConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prices.clear();
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        var section = config.getConfigurationSection("jacksparrow.prices");
        if (section == null) {
            plugin.getLogger().warning("Section jacksparrow.prices manquante dans config.yml !");
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                prices.put(mat, section.getInt(key));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Matériau inconnu dans config.yml : " + key);
            }
        }

        plugin.getLogger().info("✅ " + prices.size() + " prix JackSparrow chargés.");
    }

    public Map<Material, Integer> getPrices() { return prices; }
    public int getPrice(Material mat) { return prices.getOrDefault(mat, 0); }
    public boolean isBuyable(Material mat) { return prices.containsKey(mat); }
}