package com.citycore.quest.personal;

import com.citycore.economy.ItemTags;
import com.citycore.economy.MaterialGroups;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Objets qu'un récit « Rapporter… » demande de remettre au personnage.
 *
 * Seuls comptent les objets que le joueur s'est procurés lui-même : ni achetés à un PNJ, ni pris
 * dans un entrepôt (voir {@link ItemTags}). Les objets sont désignés comme dans config.yml
 * (« tide:carp », « handcrafted:*_chair », « #minecraft:wool »…), résolus au premier besoin :
 * les objets des mods n'existent qu'une fois le serveur démarré.
 *
 * @param needs objets demandés, tous à rapporter en une fois
 */
public record Delivery(List<Need> needs) {

    /**
     * @param tokens  objets acceptés (écriture de {@link MaterialGroups#resolveAny})
     * @param amount  quantité à rapporter
     * @param label   nom affiché (« truite de ruisseau », « chaises (toutes essences) »)
     * @param named   seuls comptent les objets renommés à l'enclume (« une bûche qui parle »)
     */
    public record Need(String tokens, int amount, String label, boolean named) {

        public Set<Material> materials() {
            return MaterialGroups.cached(tokens);
        }

        /** Exemplaires valables dans l'inventaire du joueur. */
        public int count(Player player) {
            Set<Material> mats = materials();
            if (!named) return ItemTags.countUntagged(player, mats);
            int n = 0;
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (accepts(item, mats)) n += item.getAmount();
            }
            return n;
        }

        private boolean accepts(ItemStack item, Set<Material> mats) {
            if (item == null || !mats.contains(item.getType()) || ItemTags.isTagged(item)) return false;
            if (!named) return true;
            ItemMeta meta = item.getItemMeta();
            return meta != null && meta.hasDisplayName();
        }

        /** Retire la quantité demandée (le joueur l'a, voir {@link Delivery#has}). */
        void take(Player player) {
            Set<Material> mats = materials();
            if (!named) {
                ItemTags.removeUntagged(player, mats, amount);
                return;
            }
            int left = amount;
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (left <= 0) break;
                if (!accepts(item, mats)) continue;
                int take = Math.min(item.getAmount(), left);
                item.setAmount(item.getAmount() - take);
                left -= take;
            }
        }
    }

    /** Un seul objet (ou une seule famille) à rapporter. */
    public static Delivery of(String tokens, int amount, String label) {
        return new Delivery(List.of(new Need(tokens, amount, label, false)));
    }

    /** Ajoute un objet demandé. */
    public Delivery and(String tokens, int amount, String label) {
        List<Need> list = new ArrayList<>(needs);
        list.add(new Need(tokens, amount, label, false));
        return new Delivery(List.copyOf(list));
    }

    /** Ajoute un objet demandé qui doit porter un nom donné à l'enclume. */
    public Delivery andNamed(String tokens, int amount, String label) {
        List<Need> list = new ArrayList<>(needs);
        list.add(new Need(tokens, amount, label, true));
        return new Delivery(List.copyOf(list));
    }

    /** Le joueur a-t-il sur lui tout ce qu'il faut ? */
    public boolean has(Player player) {
        for (Need need : needs) if (need.count(player) < need.amount()) return false;
        return true;
    }

    /** Remet les objets au personnage. @return false s'il en manque (rien n'est retiré) */
    public boolean take(Player player) {
        if (!has(player)) return false;
        for (Need need : needs) need.take(player);
        return true;
    }

    /** Bulle du récit : une ligne par objet, avec ce que le joueur a sur lui (« 3/4 »). */
    public List<String> lore(Player player) {
        List<String> lore = new ArrayList<>();
        for (Need need : needs) {
            int have = player == null ? 0 : Math.min(need.amount(), need.count(player));
            boolean ok = have >= need.amount();
            lore.add((ok ? "§a✔ " : "§7• ") + "§f" + need.amount() + " " + need.label()
                    + (need.named() ? " §8(renommé à l'enclume)" : "")
                    + " §7(" + (ok ? "§a" : "§f") + have + "§7/§f" + need.amount() + "§7)");
        }
        return lore;
    }
}
