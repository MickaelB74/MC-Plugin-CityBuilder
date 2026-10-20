package com.citycore.economy;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Marquage des items vendus par les PNJ ou pris (gratuitement) dans leur entrepôt, et de ce qu'on
 * fabrique avec (voir {@link TagPropagation}).
 *
 * Un item marqué :
 *   - ne peut pas être revendu à un PNJ (anti achat-revente) ;
 *   - n'est pas compté dans les quêtes de collecte des PNJ (anti farm de récompenses) ;
 *   - reste utilisable pour construire, crafter et livrer les chantiers de la ville.
 */
public final class ItemTags {

    private static NamespacedKey key;

    private ItemTags() {}

    public static void init(JavaPlugin plugin) {
        key = new NamespacedKey(plugin, "buyback");
    }

    public static NamespacedKey key() { return key; }

    public static boolean isTagged(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(key, PersistentDataType.BYTE);
    }

    /** Marque l'item comme acheté chez un PNJ (+ ligne de lore explicative). */
    public static void tag(ItemStack item, String sellerName) {
        mark(item, "§8Acheté chez " + sellerName + " §8— non revendable");
    }

    /** Marque l'item comme pris dans l'entrepôt d'un PNJ (gratuit, donc non revendable). */
    public static void tagStock(ItemStack item, String npcName) {
        mark(item, "§8Réserve de " + npcName + " §8— non revendable");
    }

    /** Marque un objet fabriqué ou cuit avec des objets marqués (sinon : entrepôt gratuit → pain → vente). */
    public static void tagDerived(ItemStack item) {
        if (isTagged(item)) return;
        mark(item, "§8Fait avec des réserves de la ville §8— non revendable");
    }

    private static void mark(ItemStack item, String line) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(line);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /* =========================
       COMPTAGE / RETRAIT
       ========================= */

    /** Items non marqués (vendables, valides pour les quêtes PNJ). */
    public static int countUntagged(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != mat || isTagged(item)) continue;
            count += item.getAmount();
        }
        return count;
    }

    /** Items non marqués d'une famille de matériaux (toutes laines, toutes chaises…). */
    public static int countUntagged(Player player, Set<Material> mats) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || !mats.contains(item.getType()) || isTagged(item)) continue;
            count += item.getAmount();
        }
        return count;
    }

    /**
     * Retire jusqu'à {@code amount} items non marqués d'une famille de matériaux.
     * @return quantités réellement retirées, par matériau
     */
    public static java.util.Map<Material, Integer> removeUntagged(Player player, Set<Material> mats, int amount) {
        java.util.Map<Material, Integer> removed = new java.util.LinkedHashMap<>();
        int toRemove = amount;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (toRemove <= 0) break;
            if (item == null || !mats.contains(item.getType()) || isTagged(item)) continue;
            int take = Math.min(item.getAmount(), toRemove);
            removed.merge(item.getType(), take, Integer::sum);
            item.setAmount(item.getAmount() - take);
            toRemove -= take;
        }
        return removed;
    }

    public static int countTagged(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != mat || !isTagged(item)) continue;
            count += item.getAmount();
        }
        return count;
    }

    /** Retire jusqu'à {@code amount} items non marqués. @return quantité réellement retirée */
    public static int removeUntagged(Player player, Material mat, int amount) {
        int toRemove = amount;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (toRemove <= 0) break;
            if (item == null || item.getType() != mat || isTagged(item)) continue;
            int take = Math.min(item.getAmount(), toRemove);
            item.setAmount(item.getAmount() - take);
            toRemove -= take;
        }
        return amount - toRemove;
    }

    /** Tous les items d'un ensemble de matériaux, marqués ou non (livraisons de chantier). */
    public static int countAny(Player player, Set<Material> mats) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && mats.contains(item.getType())) count += item.getAmount();
        }
        return count;
    }

    /** Retire jusqu'à {@code amount} items d'un ensemble de matériaux. @return quantité retirée */
    public static int removeAny(Player player, Set<Material> mats, int amount) {
        int toRemove = amount;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (toRemove <= 0) break;
            if (item == null || !mats.contains(item.getType())) continue;
            int take = Math.min(item.getAmount(), toRemove);
            item.setAmount(item.getAmount() - take);
            toRemove -= take;
        }
        return amount - toRemove;
    }

    /**
     * Donne un item au joueur en respectant la taille max des piles
     * (ex : 16 perles, 1 outil) ; le surplus tombe à ses pieds.
     */
    public static void give(Player player, ItemStack stack) {
        int remaining = stack.getAmount();
        int max       = Math.max(1, stack.getMaxStackSize());
        while (remaining > 0) {
            ItemStack part = stack.clone();
            part.setAmount(Math.min(max, remaining));
            remaining -= part.getAmount();
            player.getInventory().addItem(part).values().forEach(rest ->
                    player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
    }
}
