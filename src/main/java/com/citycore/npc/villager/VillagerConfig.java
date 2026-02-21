package com.citycore.npc.villager;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class VillagerConfig {

    private final JavaPlugin plugin;
    private final String configKey;

    private final Map<Material, SellPrice>        sellPrices   = new LinkedHashMap<>();
    private final Map<Integer, Integer>            xpThresholds = new LinkedHashMap<>();
    private final Map<Integer, List<ShopItem>>     shopItems    = new LinkedHashMap<>();
    private int    xpPerStack;
    private int    xpPerCoin;
    private double cityBuybackRatio;

    public VillagerConfig(JavaPlugin plugin, String configKey) {
        this.plugin    = plugin;
        this.configKey = configKey;
        reload();
    }

    public void reload() {
        sellPrices.clear();
        xpThresholds.clear();
        shopItems.clear();

        plugin.saveDefaultConfig();
        var config = plugin.getConfig();

        cityBuybackRatio = config.getDouble("npc-settings.city-buyback-ratio", 0.5);
        xpPerStack       = config.getInt(configKey + ".xp-per-stack", 10);
        xpPerCoin        = config.getInt(configKey + ".xp-per-coin", 1);

        // ── Prix de vente avec quantity ──────────────────────────
        ConfigurationSection sell = config.getConfigurationSection(configKey + ".sell-prices");
        if (sell != null) {
            for (String key : sell.getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    int price    = sell.getInt(key + ".price");
                    int quantity = sell.getInt(key + ".quantity", 64);
                    sellPrices.put(mat, new SellPrice(price, quantity));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Matériau inconnu : " + key);
                }
            }
        }

        // ── Seuils XP ────────────────────────────────────────────
        ConfigurationSection thresholds = config.getConfigurationSection(
                configKey + ".level-thresholds");
        if (thresholds != null) {
            for (String key : thresholds.getKeys(false)) {
                xpThresholds.put(Integer.parseInt(key), thresholds.getInt(key));
            }
        }

        // ── Shop par niveau ──────────────────────────────────────
        for (int level = 1; level <=5; level++) {
            List<ShopItem> items = new ArrayList<>();
            List<Map<?, ?>> rawList = config.getMapList(configKey + ".shop." + level);
            for (Map<?, ?> map : rawList) {
                try {
                    Material mat = Material.valueOf(map.get("material").toString().toUpperCase());
                    int price    = Integer.parseInt(map.get("price").toString());
                    int qty      = map.containsKey("quantity")
                            ? Integer.parseInt(map.get("quantity").toString()) : 1;
                    items.add(new ShopItem(mat, price, qty));
                } catch (Exception e) {
                    plugin.getLogger().warning("Item shop invalide niv." + level
                            + " : " + e.getMessage());
                }
            }
            shopItems.put(level, items);
        }

        int totalShopItems = shopItems.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("✅ Config " + configKey + " chargée — "
                + sellPrices.size() + " prix vente, "
                + totalShopItems + " items boutique.");
    }

    // ── Getters ──────────────────────────────────────────────────

    public Map<Material, SellPrice>     getSellPrices()              { return sellPrices; }
    public Map<Integer, Integer>        getXpThresholds()            { return xpThresholds; }
    public List<ShopItem>               getShopItemsForLevel(int l)  { return shopItems.getOrDefault(l, List.of()); }
    public SellPrice                    getSellPrice(Material mat)   { return sellPrices.get(mat); }
    public boolean                      isSellable(Material mat)     { return sellPrices.containsKey(mat); }
    public int                          getXpPerStack()              { return xpPerStack; }
    public int                          getXpPerCoin()               { return xpPerCoin; }
    public double                       getCityBuybackRatio()        { return cityBuybackRatio; }

    public int getCityBuybackPrice(Material mat) {
        SellPrice sp = sellPrices.get(mat);
        if (sp == null) return 0;
        return (int) Math.max(1, sp.price() * cityBuybackRatio);
    }

    // ── Records ──────────────────────────────────────────────────

    /** Prix de vente d'un matériau */
    public record SellPrice(int price, int quantity) {}

    /** Item de la boutique */
    public record ShopItem(Material material, int price, int quantity) {}
}