package com.citycore.economy;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.villager.VillagerConfig;
import com.citycore.npc.villager.VillagerConfig.Requirement;
import com.citycore.npc.villager.VillagerConfig.SellPrice;
import com.citycore.npc.villager.VillagerConfig.ShopItem;
import com.citycore.player.PlayerDataManager;
import com.citycore.util.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Cœur de l'économie des PNJ.
 *
 * Règles :
 *   1. Ce que les joueurs vendent entre dans le stock du PNJ (capacité limitée).
 *   2. Plus l'entrepôt se remplit, moins le PNJ paie (offre / demande) ;
 *      plein, il refuse d'acheter.
 *   3. Le stock se revend au prix de rachat × multiplicateur (toujours plus cher).
 *   4. Les articles fabriqués consomment des ingrédients dans les stocks (le sien ou
 *      celui d'un autre artisan) : sans matière première, pas de production.
 *   5. Les stocks sont consommés lentement (l'artisan travaille, exporte),
 *      ce qui recrée de la demande.
 */
public class MarketService {

    private static final long CONSUMPTION_PERIOD_TICKS = 20L * 60 * 5; // 5 minutes

    private final JavaPlugin        plugin;
    private final DatabaseManager   db;
    private final NPCDataManager    dataManager;
    private final PlayerDataManager playerDataManager;
    private final EconomyConfig     cfg;

    private final Map<CityNPC, VillagerConfig> configs  = new LinkedHashMap<>();
    private final Map<String, CityNPC>         npcByKey = new HashMap<>();

    public MarketService(JavaPlugin plugin, DatabaseManager db,
                         NPCDataManager dataManager, PlayerDataManager playerDataManager) {
        this.plugin            = plugin;
        this.db                = db;
        this.dataManager       = dataManager;
        this.playerDataManager = playerDataManager;
        this.cfg               = EconomyConfig.load(plugin.getConfig());
        createSupplyTable();
    }

    /* =========================
       REGISTRE DES PNJ
       ========================= */

    public void register(CityNPC npc, VillagerConfig config) {
        configs.put(npc, config);
        npcByKey.put(npc.skinId.toLowerCase(Locale.ROOT), npc);
    }

    public EconomyConfig                cfg()                  { return cfg; }
    public VillagerConfig               config(CityNPC npc)    { return configs.get(npc); }
    public Map<CityNPC, VillagerConfig> configs()              { return configs; }
    public CityNPC                      npcByKey(String key)   { return npcByKey.get(key.toLowerCase(Locale.ROOT)); }

    public boolean isEmployee(Player player, CityNPC npc) {
        return npc == playerDataManager.getJob(player.getUniqueId());
    }

    /**
     * Valeur unitaire de référence d'un matériau : prix plein payé par le PNJ qui l'achète.
     * @return 0 si aucun PNJ ne l'achète
     */
    public double unitValue(Material mat) {
        double best = 0;
        for (VillagerConfig c : configs.values()) {
            SellPrice sp = c.getSellPrice(mat);
            if (sp != null) best = Math.max(best, sp.unitValue());
        }
        return best;
    }

    /* =========================
       DEMANDE
       ========================= */

    /** Facteur appliqué au prix plein selon le remplissage de l'entrepôt. */
    public double demandFactor(int stock, int capacity) {
        if (capacity <= 0) return 1.0;
        double fill   = (double) stock / capacity;
        double plateau = cfg.fullPriceUntil() / 100.0;
        double floor   = cfg.minPricePercent() / 100.0;
        if (fill <= plateau) return 1.0;
        if (fill >= 1.0)     return floor;
        return 1.0 - (fill - plateau) / (1.0 - plateau) * (1.0 - floor);
    }

    public String demandLabel(int stock, int capacity) {
        if (capacity <= 0) return "§aConstante";
        double fill = (double) stock / capacity;
        if (fill >= 1.0)                           return "§4Entrepôt plein";
        if (fill <= cfg.fullPriceUntil() / 100.0)  return "§aForte";
        if (fill < 0.7)                            return "§eNormale";
        if (fill < 0.9)                            return "§6Faible";
        return "§cTrès faible";
    }

    /** Barre de remplissage de l'entrepôt. */
    public String stockBar(int stock, int capacity) {
        if (capacity <= 0) return "§7Stock : §f" + stock;
        int filled = (int) Math.min(10, Math.round(10.0 * stock / capacity));
        String color = filled >= 9 ? "§c" : filled >= 6 ? "§6" : "§a";
        return color + "▮".repeat(filled) + "§8" + "▮".repeat(10 - filled)
                + " §7" + stock + "§8/§7" + capacity;
    }

    /* =========================
       VENTE (JOUEUR → PNJ)
       ========================= */

    /**
     * Calcule une vente lot par lot : chaque lot fait monter le stock et baisser le prix
     * du suivant. S'arrête à la capacité de l'entrepôt.
     */
    public SellQuote quoteSell(CityNPC npc, Material mat, int availableItems,
                               int maxLots, boolean employee) {
        SellPrice sp = config(npc).getSellPrice(mat);
        if (sp == null) return new SellQuote(0, 0, 0);

        int stock = dataManager.getInventoryAmount(npc, mat);
        int limit = Math.min(availableItems / sp.quantity(), maxLots);
        int lots = 0;
        double total = 0;
        while (lots < limit) {
            int s = stock + lots * sp.quantity();
            if (sp.capacity() > 0 && s + sp.quantity() > sp.capacity()) break;
            total += sp.price() * demandFactor(s, sp.capacity());
            lots++;
        }
        double bonus = employee ? 1 + cfg.jobSellBonus() / 100.0 : 1.0;
        return new SellQuote(lots, (int) Math.floor(total * bonus), lots * sp.quantity());
    }

    /** Prix actuel d'un lot (affichage). */
    public double currentLotPrice(CityNPC npc, Material mat, boolean employee) {
        SellPrice sp = config(npc).getSellPrice(mat);
        if (sp == null) return 0;
        int stock = dataManager.getInventoryAmount(npc, mat);
        double p = sp.price() * demandFactor(stock, sp.capacity());
        return employee ? p * (1 + cfg.jobSellBonus() / 100.0) : p;
    }

    public record SellQuote(int lots, int coins, int items) {}

    /* =========================
       REVENTE DU STOCK (PNJ → JOUEUR)
       ========================= */

    public int resaleLotPrice(SellPrice sp, boolean employee) {
        double p = sp.price() * cfg.resaleMultiplier();
        if (employee) p *= 1 - cfg.jobShopDiscount() / 100.0;
        return Math.max(1, ceil(p));
    }

    public int shopPrice(ShopItem item, boolean employee) {
        if (!employee) return item.price();
        return Math.max(1, ceil(item.price() * (1 - cfg.jobShopDiscount() / 100.0)));
    }

    /** Arrondi supérieur insensible aux erreurs flottantes (17.000000000004 → 17). */
    private static int ceil(double v) {
        return (int) Math.ceil(v - 1e-9);
    }

    /* =========================
       PRODUCTION DEPUIS LE STOCK
       ========================= */

    public CityNPC supplierOf(CityNPC seller, Requirement r) {
        return r.isOwnStock() ? seller : npcByKey(r.supplierKey());
    }

    public int stockOf(CityNPC npc, Set<Material> mats) {
        if (npc == null) return 0;
        int total = 0;
        for (Material m : mats) total += dataManager.getInventoryAmount(npc, m);
        return total;
    }

    /** Nombre d'achats possibles selon les ingrédients et l'approvisionnement. */
    public int availableCount(CityNPC seller, ShopItem item) {
        int count = Integer.MAX_VALUE;
        for (Requirement r : item.requires()) {
            CityNPC supplier = supplierOf(seller, r);
            if (supplier == null) return 0;
            count = Math.min(count, stockOf(supplier, r.materials()) / r.amount());
        }
        if (item.supply() != null) count = Math.min(count, getSupply(seller, item));
        return count;
    }

    /** Consomme les ingrédients et l'approvisionnement d'un achat. @return false si indisponible */
    public boolean consume(CityNPC seller, ShopItem item) {
        if (availableCount(seller, item) < 1) return false;
        for (Requirement r : item.requires()) {
            removeFromStock(supplierOf(seller, r), r.materials(), r.amount());
        }
        if (item.supply() != null) takeSupply(seller, item);
        return true;
    }

    /** Retire une quantité d'un groupe de matériaux, en puisant d'abord dans le plus abondant. */
    public void removeFromStock(CityNPC npc, Set<Material> mats, int amount) {
        List<Material> sorted = new ArrayList<>(mats);
        sorted.sort(Comparator.comparingInt((Material m) -> dataManager.getInventoryAmount(npc, m)).reversed());
        int remaining = amount;
        for (Material m : sorted) {
            if (remaining <= 0) break;
            int take = Math.min(dataManager.getInventoryAmount(npc, m), remaining);
            if (take > 0 && dataManager.removeFromInventory(npc, m, take)) remaining -= take;
        }
    }

    /* =========================
       APPROVISIONNEMENT LIMITÉ
       ========================= */

    private void createSupplyTable() {
        try {
            db.execute("""
                CREATE TABLE IF NOT EXISTS npc_shop_supply (
                    npc_tag    TEXT    NOT NULL,
                    entry_key  TEXT    NOT NULL,
                    amount     INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (npc_tag, entry_key)
                )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Unités disponibles (reconstitution calculée à la volée). */
    public int getSupply(CityNPC npc, ShopItem item) {
        long[] row = readSupply(npc, item);
        if (row == null) return item.supply().max();
        return (int) restock(item, row, System.currentTimeMillis())[0];
    }

    /** Minutes avant la prochaine unité, 0 si l'article est plein. */
    public long minutesUntilRestock(CityNPC npc, ShopItem item) {
        long[] row = readSupply(npc, item);
        if (row == null) return 0;
        long now = System.currentTimeMillis();
        long[] state = restock(item, row, now);
        if (state[0] >= item.supply().max()) return 0;
        long interval = item.supply().restockMinutes() * 60_000L;
        long remainingMs = interval - (now - state[1]) % interval;
        return Math.max(1, (remainingMs + 59_999) / 60_000);
    }

    private void takeSupply(CityNPC npc, ShopItem item) {
        long now = System.currentTimeMillis();
        long[] row = readSupply(npc, item);
        long amount;
        long updated;
        if (row == null) {
            amount  = item.supply().max();
            updated = now;
        } else {
            long[] state = restock(item, row, now);
            amount  = state[0];
            updated = state[1];
        }
        if (amount <= 0) return;
        // Quand l'article était plein, la reconstitution démarre maintenant
        if (amount >= item.supply().max()) updated = now;
        writeSupply(npc, item, amount - 1, updated);
    }

    /** @return {quantité actuelle, date de référence alignée sur la dernière reconstitution} */
    private long[] restock(ShopItem item, long[] row, long now) {
        long interval = item.supply().restockMinutes() * 60_000L;
        long amount   = row[0];
        long updated  = row[1];
        if (amount >= item.supply().max()) return new long[]{item.supply().max(), now};
        long gained = Math.max(0, (now - updated) / interval);
        amount = Math.min(item.supply().max(), amount + gained);
        updated = amount >= item.supply().max() ? now : updated + gained * interval;
        return new long[]{amount, updated};
    }

    private long[] readSupply(CityNPC npc, ShopItem item) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT amount, updated_at FROM npc_shop_supply WHERE npc_tag = ? AND entry_key = ?");
            ps.setString(1, npc.tag);
            ps.setString(2, item.key());
            ResultSet rs = ps.executeQuery();
            long[] row = rs.next() ? new long[]{rs.getLong("amount"), rs.getLong("updated_at")} : null;
            ps.close();
            return row;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void writeSupply(CityNPC npc, ShopItem item, long amount, long updated) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO npc_shop_supply (npc_tag, entry_key, amount, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(npc_tag, entry_key)
                DO UPDATE SET amount = excluded.amount, updated_at = excluded.updated_at
            """);
            ps.setString(1, npc.tag);
            ps.setString(2, item.key());
            ps.setLong(3, amount);
            ps.setLong(4, updated);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       CRÉATION DES ITEMS VENDUS
       ========================= */

    /** Construit l'item d'un article (enchantements, potion, livre) et le marque non revendable. */
    public ItemStack createStack(ShopItem item, String sellerName) {
        ItemStack stack = new ItemStack(item.material(), item.quantity());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (item.displayName() != null) meta.setDisplayName(item.displayName());
            if (!item.lore().isEmpty())     meta.setLore(new ArrayList<>(item.lore()));
            if (meta instanceof EnchantmentStorageMeta esm) {
                item.enchantments().forEach((e, lvl) -> esm.addStoredEnchant(e, lvl, true));
            } else {
                item.enchantments().forEach((e, lvl) -> meta.addEnchant(e, lvl, true));
            }
            if (item.potion() != null && meta instanceof PotionMeta pm) {
                pm.setBasePotionType(item.potion());
            }
            if (item.book() != null && meta instanceof BookMeta bm) {
                bm.setTitle(item.book().title());
                bm.setAuthor(item.book().author());
                bm.setPages(item.book().pages());
            }
            stack.setItemMeta(meta);
        }
        ItemTags.tag(stack, sellerName);
        return stack;
    }

    /* =========================
       CONSOMMATION DES STOCKS
       ========================= */

    /**
     * Toutes les 5 minutes, tant qu'au moins un joueur est connecté, chaque PNJ consomme
     * une fraction de sa capacité (travail, exportations). Cela recrée de la demande et
     * empêche une ferme automatique de saturer définitivement un marché.
     */
    public void startConsumptionTask() {
        if (cfg.consumptionPercentPerHour() <= 0) return;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            double perPeriod = cfg.consumptionPercentPerHour() / 100.0 / 12.0;
            for (Map.Entry<CityNPC, VillagerConfig> e : configs.entrySet()) {
                for (Map.Entry<Material, SellPrice> sp : e.getValue().getSellPrices().entrySet()) {
                    int capacity = sp.getValue().capacity();
                    if (capacity <= 0) continue;
                    dataManager.removeUpTo(e.getKey(), sp.getKey(),
                            (int) Math.ceil(capacity * perPeriod));
                }
            }
        }, CONSUMPTION_PERIOD_TICKS, CONSUMPTION_PERIOD_TICKS);
    }
}
