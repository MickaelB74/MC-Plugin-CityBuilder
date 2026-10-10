package com.citycore.ui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Objets posés dans les menus « Valmont ».
 *
 * Les boutons sont dessinés dans le titre ; dessous, chaque case reçoit un objet invisible
 * (papier au modèle personnalisé, rendu transparent par le pack) qui porte la bulle d'info.
 * Le papier ordinaire garde son apparence : seul ce numéro de modèle est concerné.
 */
public final class UiItems {

    /** Numéro de modèle de l'objet invisible (voir le pack : models/item/paper.json). */
    public static final int BLANK_MODEL = 7_370_001;

    private UiItems() {}

    /** Objet invisible avec bulle d'info (nom + lignes, codes couleur §). */
    public static ItemStack blank(String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(BLANK_MODEL);
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** Objet invisible sans bulle (zone cliquable silencieuse). */
    public static ItemStack silent() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(BLANK_MODEL);
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Tête d'un habitant avec la texture de son skin ({value, signature}, voir
     * NPCManager#resolveSkin) ; tête par défaut si le skin est inconnu.
     */
    public static ItemStack head(String[] skin, String key, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return item;
        if (skin != null && skin.length > 0 && skin[0] != null && !skin[0].isBlank()) {
            // Profil fixe par habitant : le client garde la texture en cache d'une ouverture à l'autre
            PlayerProfile profile = Bukkit.createProfile(
                    UUID.nameUUIDFromBytes(("citycore:" + key).getBytes(StandardCharsets.UTF_8)));
            String signature = skin.length > 1 && skin[1] != null && !skin[1].isBlank() ? skin[1] : null;
            profile.setProperty(new ProfileProperty("textures", skin[0], signature));
            meta.setPlayerProfile(profile);
        }
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /** Objet réel (marchandise, objectif) avec une bulle d'info personnalisée. */
    public static ItemStack shown(Material material, String name, List<String> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glint) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }
}
