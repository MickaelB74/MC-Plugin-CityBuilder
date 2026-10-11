package com.citycore.inhabitant;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Apparence des habitants et des soldats (plan « Valmont vivante ») : des PNJ à apparence de
 * joueur, dont le skin est pris dans une liste de config.yml — {@code habitants.skins} pour les
 * habitants, {@code soldats.skins} pour l'uniforme que prend un habitant devenu soldat.
 *
 * Chaque entrée donne soit un skin importé sur mineskin.org (value + signature), soit le pseudo
 * d'un compte Minecraft (name, téléchargé par Citizens). Les entrées vides sont ignorées.
 */
public final class InhabitantSkins {

    /** Liste des skins des habitants. */
    public static final String HABITANTS = "habitants.skins";
    /** Liste des skins des soldats (uniforme de la garde). */
    public static final String SOLDATS   = "soldats.skins";

    /** Un skin de la liste : texture signée, ou pseudo d'un compte. */
    public record Skin(String name, String value, String signature) {
        boolean isTexture() {
            return !value.isBlank() && !signature.isBlank();
        }
    }

    private final JavaPlugin plugin;
    private final String     path;
    private final List<Skin> skins = new ArrayList<>();
    private int ignored;  // entrées vides ou incomplètes
    private int next;     // distribution à tour de rôle : deux voisins n'ont pas le même skin

    /** @param path liste lue dans config.yml : {@link #HABITANTS} ou {@link #SOLDATS} */
    public InhabitantSkins(JavaPlugin plugin, String path) {
        this.plugin = plugin;
        this.path   = path;
        reload();
    }

    /** Relit la liste directement dans le fichier config.yml (sans recharger toute la config). */
    public void reload() {
        skins.clear();
        ignored = 0;
        File file = new File(plugin.getDataFolder(), "config.yml");
        ConfigurationSection root = file.exists() ? YamlConfiguration.loadConfiguration(file) : plugin.getConfig();
        for (Map<?, ?> entry : root.getMapList(path)) {
            String name      = text(entry.get("name"));
            String value     = text(entry.get("value"));
            String signature = text(entry.get("signature"));
            Skin skin = new Skin(name, value, signature);
            if (skin.isTexture() || !name.isBlank()) skins.add(skin);
            else ignored++;
        }
    }

    /** Nombre de skins utilisables. */
    public int size() {
        return skins.size();
    }

    public boolean isEmpty() {
        return skins.isEmpty();
    }

    /** Numéro du prochain skin à distribuer (à tour de rôle), ou -1 si la liste est vide. */
    public int nextIndex() {
        return skins.isEmpty() ? -1 : Math.floorMod(next++, skins.size());
    }

    /** Nombre d'entrées vides ou incomplètes (value sans signature…). */
    public int ignored() {
        return ignored;
    }

    /**
     * Donne au PNJ (de type joueur) le skin suivant de la liste ; liste vide : skin par défaut
     * de Minecraft, choisi par le jeu d'après l'identifiant du PNJ (varié d'un PNJ à l'autre).
     * À appeler avant l'apparition du PNJ, ou suivi d'une réapparition.
     * @return numéro du skin dans la liste, ou -1 pour un skin par défaut de Minecraft
     */
    public int apply(NPC npc) {
        if (skins.isEmpty()) {
            apply(npc, -1);
            return -1;
        }
        int index = Math.floorMod(next++, skins.size());
        apply(npc, index);
        return index;
    }

    /** Donne au PNJ le skin numéro index (-1 ou hors liste : skin par défaut de Minecraft). */
    public void apply(NPC npc, int index) {
        SkinTrait trait = npc.getOrAddTrait(SkinTrait.class);
        if (index < 0 || index >= skins.size()) {
            // Pas de téléchargement d'après le nom du PNJ : le jeu choisit un skin par défaut
            trait.setFetchDefaultSkin(false);
            return;
        }
        Skin skin = skins.get(index);
        if (skin.isTexture()) {
            // Nom de cache propre à la liste : habitants.skins → « citycore_habitants_0 »
            trait.setSkinPersistent("citycore_" + path.substring(0, path.indexOf('.')) + "_" + index,
                    skin.signature(), skin.value());
        } else {
            trait.setFetchDefaultSkin(true);
            trait.setSkinName(skin.name());
        }
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
