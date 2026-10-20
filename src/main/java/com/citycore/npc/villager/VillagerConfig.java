package com.citycore.npc.villager;

import com.citycore.economy.MaterialGroups;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.util.*;

/**
 * Configuration commerciale d'un PNJ (section <clé du PNJ> de config.yml).
 *
 * <pre>
 * bastien:
 *   xp-per-stack: 1
 *   xp-per-coin: 1
 *   level-thresholds: {2: 300, 3: 1200, 4: 3500, 5: 8000}
 *   sell-prices:            # ce que le PNJ rachète aux joueurs (va dans son stock)
 *     OAK_LOG: {price: 5, quantity: 16, capacity: 1536}
 *   shop:                   # ce que le PNJ vend, par niveau
 *     1:
 *       - {material: CHARCOAL, price: 6, quantity: 8, requires: {"#LOGS": 8}}
 *       - {material: OAK_SAPLING, price: 3, quantity: 4, supply: {max: 8, restock-minutes: 30}}
 * </pre>
 *
 * Ingrédients (requires) : consommés dans le stock du PNJ, ou dans celui d'un autre PNJ
 * avec la syntaxe "cle_pnj:MATERIAU" (ex : "thorvald:IRON_INGOT"). "#GROUPE" accepte
 * n'importe quel matériau du groupe (voir {@link MaterialGroups}).
 * Approvisionnement (supply) : stock propre à l'article, qui se reconstitue avec le temps.
 */
public class VillagerConfig {

    private final JavaPlugin plugin;
    private final String     configKey;

    private final Map<Material, SellPrice>    sellPrices   = new LinkedHashMap<>();
    private final Map<Integer, Integer>        xpThresholds = new LinkedHashMap<>();
    private final Map<Integer, List<ShopItem>> shopItems    = new LinkedHashMap<>();
    /** Objets de mods introuvables sur ce serveur (mod absent) : ignorés, résumés en une ligne. */
    private final Set<String> missingModItems = new TreeSet<>();
    /** Objets de mods introuvables alors que le mod est présent (erreur d'identifiant) : avertis un par un. */
    private final List<String> brokenModItems = new ArrayList<>();
    private int xpPerStack;
    private int xpPerCoin;

    public VillagerConfig(JavaPlugin plugin, String configKey) {
        this.plugin    = plugin;
        this.configKey = configKey;
        reload();
    }

    public void reload() {
        sellPrices.clear();
        xpThresholds.clear();
        shopItems.clear();
        missingModItems.clear();
        brokenModItems.clear();

        plugin.saveDefaultConfig();
        var config = plugin.getConfig();

        xpPerStack = config.getInt(configKey + ".xp-per-stack", 1);
        xpPerCoin  = config.getInt(configKey + ".xp-per-coin", 1);

        // ── Prix de rachat (joueur → PNJ) ────────────────────────
        // Une clé de groupe (« #minecraft:wool », « handcrafted:*_chair », « #LOGS ») vaut pour
        // chacun de ses objets, avec le même lot, le même prix et la même capacité ; une clé simple
        // déclarée à part l'emporte sur le groupe.
        ConfigurationSection sell = config.getConfigurationSection(configKey + ".sell-prices");
        if (sell != null) {
            Map<Material, SellPrice> fromGroups = new LinkedHashMap<>();
            for (String key : sell.getKeys(false)) {
                if (!com.citycore.integration.ModIntegrations.allows(
                        sell.getString(key + ".avec-mod"), sell.getString(key + ".sans-mod"))) continue;
                int price    = sell.getInt(key + ".price");
                int quantity = Math.max(1, sell.getInt(key + ".quantity", 64));
                int capacity = Math.max(0, sell.getInt(key + ".capacity", 0));
                SellPrice sp = new SellPrice(price, quantity, capacity);
                if (isGroup(key)) {
                    Set<Material> mats = MaterialGroups.resolveAny(key);
                    if (mats.isEmpty()) {
                        missing(key, "groupe vide dans sell-prices : " + key);
                        continue;
                    }
                    for (Material m : mats) if (m.isItem()) fromGroups.putIfAbsent(m, sp);
                    continue;
                }
                Material mat = material(key);
                if (mat == null) {
                    if (!com.citycore.integration.ItemIds.isModId(key)) warn("matériau inconnu dans sell-prices : " + key);
                    continue;
                }
                sellPrices.put(mat, sp);
            }
            fromGroups.forEach(sellPrices::putIfAbsent);
        }

        // ── Seuils XP ────────────────────────────────────────────
        ConfigurationSection thresholds = config.getConfigurationSection(
                configKey + ".level-thresholds");
        if (thresholds != null) {
            for (String key : thresholds.getKeys(false)) {
                try {
                    xpThresholds.put(Integer.parseInt(key.trim()), thresholds.getInt(key));
                } catch (NumberFormatException e) {
                    warn("seuil XP invalide : " + key);
                }
            }
        }

        // ── Boutique par niveau ──────────────────────────────────
        for (int level = 1; level <= 5; level++) {
            List<ShopItem> items = new ArrayList<>();
            List<Map<?, ?>> rawList = config.getMapList(configKey + ".shop." + level);
            for (int i = 0; i < rawList.size(); i++) {
                try {
                    ShopItem item = parseShopItem(level, i, rawList.get(i));
                    if (item != null) items.add(item);
                } catch (Exception e) {
                    warn("article de boutique invalide (niv." + level + ", #" + i + ") : "
                            + e.getMessage());
                }
            }
            shopItems.put(level, items);
        }

        int totalShopItems = shopItems.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("✅ Config " + configKey + " chargée — "
                + sellPrices.size() + " prix de rachat, "
                + totalShopItems + " articles en boutique.");
        if (!missingModItems.isEmpty()) {
            plugin.getLogger().info("   [" + configKey + "] objets de mods absents du serveur, ignorés : "
                    + String.join(", ", missingModItems));
        }
        for (String problem : brokenModItems) {
            // Le mod est installé mais l'objet n'existe pas (mod mis à jour ?) : l'article est ignoré
            plugin.getLogger().warning("[" + configKey + "] " + problem + " — ignoré.");
        }
    }

    /** Matériau d'un identifiant (vanilla ou « mod:objet ») ; note les objets de mods absents. */
    private Material material(String id) {
        Material mat = com.citycore.integration.ItemIds.resolve(id);
        if (mat == null && com.citycore.integration.ItemIds.isModId(id)) {
            missing(id, "objet introuvable « " + id.trim() + " » alors que son mod est installé");
        }
        return mat;
    }

    /**
     * Objet ou groupe d'un mod introuvable : mod absent (cas normal, résumé en une ligne), ou mod
     * présent (identifiant faux ou mod mis à jour : avertissement détaillé).
     */
    private void missing(String id, String problem) {
        String ns = com.citycore.integration.ItemIds.namespace(id.replace("#", "").replace("-", "").trim());
        if (ns.equals("minecraft")) warn(problem);
        else if (com.citycore.integration.ModIntegrations.present(ns)) brokenModItems.add(problem);
        else missingModItems.add(id.trim());
    }

    /** Clé de groupe : tag (#…), motif (*) ou liste (,). */
    private static boolean isGroup(String token) {
        return token.startsWith("#") || token.contains("*") || token.contains(",");
    }

    /* =========================
       PARSING D'UN ARTICLE
       ========================= */

    private ShopItem parseShopItem(int level, int index, Map<?, ?> map) {
        // Contenu facultatif : article proposé seulement avec (ou sans) un mod
        if (!com.citycore.integration.ModIntegrations.allows(text(map.get("avec-mod")), text(map.get("sans-mod")))) return null;
        String id = String.valueOf(map.get("material"));
        Material mat = material(id);
        if (mat == null) {
            if (!com.citycore.integration.ItemIds.isModId(id)) warn("matériau inconnu en boutique : " + id);
            return null;
        }
        int price    = toInt(map.get("price"), 0);
        int quantity = Math.max(1, toInt(map.get("quantity"), 1));

        // Ingrédients consommés dans un stock
        List<Requirement> requires = new ArrayList<>();
        if (map.get("requires") instanceof Map<?, ?> req) {
            for (Map.Entry<?, ?> e : req.entrySet()) {
                String raw = String.valueOf(e.getKey()).trim();
                String supplier = null;
                String token = raw;
                // « thorvald:IRON_INGOT » : stock d'un autre artisan (préfixe = clé d'un PNJ) ;
                // « tide:carp », « #minecraft:wool » : objet ou groupe pris dans le stock du vendeur
                int colon = raw.indexOf(':');
                if (colon > 0 && isNpcKey(raw.substring(0, colon))) {
                    supplier = raw.substring(0, colon).toLowerCase(Locale.ROOT);
                    token    = raw.substring(colon + 1);
                }
                Set<Material> mats = MaterialGroups.resolveAny(token);
                if (mats.isEmpty()) {
                    missing(token, "ingrédient inconnu pour " + mat + " : " + raw);
                    return null;
                }
                requires.add(new Requirement(supplier, token,
                        Collections.unmodifiableSet(mats), Math.max(1, toInt(e.getValue(), 1))));
            }
        }

        // Approvisionnement limité
        Supply supply = null;
        if (map.get("supply") instanceof Map<?, ?> sup) {
            supply = new Supply(Math.max(1, toInt(sup.get("max"), 1)),
                    Math.max(1, toInt(sup.get("restock-minutes"), 60)));
        }

        // Présentation
        String name = map.get("name") != null ? color(String.valueOf(map.get("name"))) : null;
        List<String> lore = new ArrayList<>();
        if (map.get("lore") instanceof List<?> l) l.forEach(o -> lore.add(color(String.valueOf(o))));

        // Enchantements (clés vanilla : protection, efficiency, mending…)
        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        if (map.get("enchantments") instanceof Map<?, ?> ench) {
            for (Map.Entry<?, ?> e : ench.entrySet()) {
                String key = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
                if (enchantment == null) {
                    warn("enchantement inconnu : " + key);
                    continue;
                }
                enchants.put(enchantment, Math.max(1, toInt(e.getValue(), 1)));
            }
        }

        // Potion (POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW)
        PotionType potion = null;
        if (map.get("potion") != null) {
            try {
                potion = PotionType.valueOf(String.valueOf(map.get("potion")).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warn("type de potion inconnu : " + map.get("potion"));
            }
        }

        // Livre écrit (lore du monde)
        BookData book = null;
        if (map.get("book") instanceof Map<?, ?> b) {
            List<String> pages = new ArrayList<>();
            if (b.get("pages") instanceof List<?> p) p.forEach(o -> pages.add(color(String.valueOf(o))));
            book = new BookData(
                    String.valueOf(b.get("title") != null ? b.get("title") : "Sans titre"),
                    String.valueOf(b.get("author") != null ? b.get("author") : "Inconnu"),
                    pages);
        }

        // Données propres à un mod (« [irons_spellbooks:spell_container={…}] » : sort d'un parchemin,
        // compétence d'un livre d'Epic Fight…) : vérifiées tout de suite, l'article est ignoré si
        // le serveur les refuse
        String components = map.get("components") != null ? String.valueOf(map.get("components")).trim() : null;
        if (components != null && !components.isEmpty()) {
            String full = com.citycore.integration.ItemIds.keyOf(mat) + components;
            try {
                org.bukkit.Bukkit.getItemFactory().createItemStack(full);
            } catch (RuntimeException ex) {
                warn("données d'objet refusées pour " + id + " (" + ex.getMessage() + ") — article ignoré");
                return null;
            }
        } else {
            components = null;
        }

        String entryKey = "L" + level + "#" + index + ":" + mat.name();
        return new ShopItem(entryKey, level, mat, price, quantity, List.copyOf(requires), supply,
                name, List.copyOf(lore), Collections.unmodifiableMap(enchants), potion, book, components);
    }

    /** Clé de section d'un habitant (« thorvald ») : préfixe d'un ingrédient pris chez lui. */
    private static boolean isNpcKey(String prefix) {
        for (com.citycore.npc.CityNPC npc : com.citycore.npc.CityNPC.values()) {
            if (npc.skinId.equalsIgnoreCase(prefix)) return true;
        }
        return false;
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int toInt(Object o, int def) {
        if (o == null) return def;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(o)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void warn(String msg) {
        plugin.getLogger().warning("[" + configKey + "] " + msg);
    }

    /* =========================
       GETTERS
       ========================= */

    /**
     * Multiplicateur de la capacité de l'entrepôt : le niveau de l'atelier de l'artisan
     * (phase 2 du plan « Valmont vivante », voir BuildingInspector). 1 par défaut.
     */
    private java.util.function.DoubleSupplier capacityMultiplier = () -> 1.0;

    public void setCapacityMultiplier(java.util.function.DoubleSupplier capacityMultiplier) {
        this.capacityMultiplier = capacityMultiplier;
    }

    /** Prix de rachat avec la capacité de l'entrepôt ajustée au niveau de l'atelier. */
    private SellPrice scaled(SellPrice sp) {
        double m = capacityMultiplier.getAsDouble();
        if (sp == null || sp.capacity() <= 0 || m == 1.0) return sp;
        return new SellPrice(sp.price(), sp.quantity(), (int) Math.round(sp.capacity() * m));
    }

    public String                    getConfigKey()              { return configKey; }
    /** Prix de rachat (capacités ajustées au niveau de l'atelier), dans l'ordre de config.yml. */
    public Map<Material, SellPrice>  getSellPrices() {
        Map<Material, SellPrice> view = new LinkedHashMap<>();
        sellPrices.forEach((mat, sp) -> view.put(mat, scaled(sp)));
        return view;
    }
    public Map<Integer, Integer>     getXpThresholds()           { return xpThresholds; }
    public List<ShopItem>            getShopItemsForLevel(int l) { return shopItems.getOrDefault(l, List.of()); }
    public SellPrice                 getSellPrice(Material mat)  { return scaled(sellPrices.get(mat)); }
    public boolean                   isSellable(Material mat)    { return sellPrices.containsKey(mat); }
    public int                       getXpPerStack()             { return xpPerStack; }
    public int                       getXpPerCoin()              { return xpPerCoin; }

    /** Tous les articles, tous niveaux confondus, dans l'ordre d'affichage. */
    public List<ShopItem> getAllShopItems() {
        List<ShopItem> all = new ArrayList<>();
        shopItems.values().forEach(all::addAll);
        return all;
    }

    public ShopItem getShopItem(String entryKey) {
        for (List<ShopItem> list : shopItems.values()) {
            for (ShopItem item : list) {
                if (item.key().equals(entryKey)) return item;
            }
        }
        return null;
    }

    /* =========================
       RECORDS
       ========================= */

    /**
     * Prix de rachat d'un matériau.
     * @param price    coins payés pour un lot, à pleine demande
     * @param quantity taille du lot
     * @param capacity stock maximum du PNJ (0 = illimité)
     */
    public record SellPrice(int price, int quantity, int capacity) {
        public double unitValue() { return (double) price / quantity; }
    }

    /**
     * Ingrédient d'un article.
     * @param supplierKey clé config du PNJ fournisseur, null = stock du vendeur
     */
    public record Requirement(String supplierKey, String token, Set<Material> materials, int amount) {
        public boolean isOwnStock() { return supplierKey == null; }
    }

    /** Approvisionnement propre à un article : {@code max} unités, +1 toutes les {@code restockMinutes}. */
    public record Supply(int max, int restockMinutes) {}

    public record BookData(String title, String author, List<String> pages) {}

    /** Article de boutique. */
    public record ShopItem(
            String key,
            int level,
            Material material,
            int price,
            int quantity,
            List<Requirement> requires,
            Supply supply,
            String displayName,
            List<String> lore,
            Map<Enchantment, Integer> enchantments,
            PotionType potion,
            BookData book,
            /* Composants d'objet en texte (« [mod:composant=…] »), ou null. */
            String components
    ) {
        /** Article "spécial" : ne se compare pas à l'item de base (enchanté, potion, livre, données de mod…). */
        public boolean isSpecial() {
            return displayName != null || !enchantments.isEmpty() || potion != null || book != null
                    || components != null;
        }
    }
}
