package com.citycore.ui;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/** Boîte à outils partagée par les écrans « Valmont ». */
public final class UiKit {

    private final JavaPlugin  plugin;
    private final UiAtlas     atlas;
    private final UiPack      pack;
    private final UiPortraits portraits;
    private final Economy     economy;
    private boolean           brokenLogged;

    public UiKit(JavaPlugin plugin, UiAtlas atlas, UiPack pack, UiPortraits portraits, Economy economy) {
        this.plugin    = plugin;
        this.atlas     = atlas;
        this.pack      = pack;
        this.portraits = portraits;
        this.economy   = economy;
    }

    public JavaPlugin  plugin()    { return plugin; }
    public UiPortraits portraits() { return portraits; }
    public Economy     economy()   { return economy; }

    public UiCanvas canvas() {
        return new UiCanvas(atlas);
    }

    /** Ligne libre (en-tête de la liste des joueurs…) commençant à originX. */
    public UiCanvas canvas(int originX) {
        return new UiCanvas(atlas, originX);
    }

    /** Le joueur a-t-il le pack chargé (et n'a pas choisi les menus classiques) ? */
    public boolean active(Player player) {
        return pack.isReady(player);
    }

    /**
     * Ouvre un menu illustré ; en cas d'erreur de dessin, renvoie false pour que l'appelant
     * affiche le menu classique (une seule trace dans la console).
     */
    public boolean show(UiMenu menu) {
        try {
            menu.open();
            return true;
        } catch (RuntimeException e) {
            if (!brokenLogged) {
                brokenLogged = true;
                plugin.getLogger().log(Level.SEVERE, "Menu illustré impossible à afficher — menu classique utilisé", e);
            }
            return false;
        }
    }
}
