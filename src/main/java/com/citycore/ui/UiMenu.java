package com.citycore.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu « Valmont » : un coffre de 6 rangées dont le titre dessine toute l'interface.
 *
 * Le menu est son propre {@link InventoryHolder} : les clics sont reconnus par le type du
 * propriétaire de l'inventaire (jamais par le titre), et chaque case peut porter une action.
 */
public abstract class UiMenu implements InventoryHolder {

    public static final int SIZE = 54;

    protected final UiKit  kit;
    protected final Player player;

    private final ItemStack[] contents = new ItemStack[SIZE];
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private Inventory inventory;

    protected UiMenu(UiKit kit, Player player) {
        this.kit    = kit;
        this.player = player;
    }

    /** Dessine le titre et place les objets et actions. */
    protected abstract void draw(UiCanvas canvas);

    public final void open() {
        Arrays.fill(contents, null);
        actions.clear();
        UiCanvas canvas = kit.canvas();
        draw(canvas);
        inventory = Bukkit.createInventory(this, SIZE, windowTitle(canvas));
        inventory.setContents(contents);
        player.openInventory(inventory);
    }

    /** Titre de la fenêtre : le dessin ; un menu classique (sans le pack) y met un texte simple. */
    protected net.kyori.adventure.text.Component windowTitle(UiCanvas canvas) {
        return canvas.build();
    }

    /** Remplace l'objet d'une case du menu ouvert (affichage vivant : compte de joueurs…). */
    protected final void refresh(int col, int row, ItemStack item) {
        int s = slot(col, row);
        contents[s] = item;
        if (inventory != null) inventory.setItem(s, item);
    }

    /* =========================
       CASES
       ========================= */

    public static int slot(int col, int row) {
        return row * 9 + col;
    }

    /** Objet dans une case, avec action facultative. */
    protected final void item(int col, int row, ItemStack item, Consumer<ClickType> action) {
        int s = slot(col, row);
        contents[s] = item;
        if (action != null) actions.put(s, action);
    }

    /** Zone de cases invisibles (bouton dessiné) portant la même bulle et la même action. */
    protected final void zone(int col, int row, int cols, int rows, String name, List<String> lore,
                              Consumer<ClickType> action) {
        ItemStack blank = UiItems.blank(name, lore);
        for (int r = row; r < row + rows; r++) {
            for (int c = col; c < col + cols; c++) item(c, r, blank, action);
        }
    }

    /* =========================
       ÉVÉNEMENTS
       ========================= */

    final void click(int slot, ClickType type) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) action.accept(type);
    }

    /** Appelé à la fermeture du menu (y compris quand un autre menu le remplace). */
    protected void onClose() {}

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
