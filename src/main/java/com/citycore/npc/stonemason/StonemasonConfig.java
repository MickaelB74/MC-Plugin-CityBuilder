package com.citycore.npc.stonemason;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public class StonemasonConfig {

    private final JavaPlugin plugin;
    // Material → prix par stack
    private final Map<Material, Integer> prices = new LinkedHashMap<>();

    public StonemasonConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prices.clear();
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        var section = config.getConfigurationSection("stonemason.prices");
        if (section == null) {
            plugin.getLogger().warning("Section stonemason.prices manquante dans config.yml !");
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                int price = section.getInt(key);
                prices.put(mat, price);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Matériau inconnu dans config.yml : " + key);
            }
        }

        plugin.getLogger().info("✅ " + prices.size() + " prix tailleur chargés.");
    }

    public Map<Material, Integer> getPrices() {
        return prices;
    }

    public int getPrice(Material mat) {
        return prices.getOrDefault(mat, 0);
    }

    public boolean isBuyable(Material mat) {
        return prices.containsKey(mat);
    }
}