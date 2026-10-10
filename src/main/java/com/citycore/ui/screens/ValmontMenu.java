package com.citycore.ui.screens;

import com.citycore.ui.UiCanvas;
import com.citycore.ui.UiKit;
import com.citycore.ui.UiMenu;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.IntConsumer;

import static com.citycore.ui.UiLayout.*;

/** Éléments communs des écrans « Valmont » : bandeau, en-tête, barre, pagination, bourse. */
abstract class ValmontMenu extends UiMenu {

    // Couleurs des textes (multipliées par les glyphes blancs de la police)
    static final int GOLD  = 0xF1CF5E;
    static final int CREAM = 0xF6EBCD;
    static final int INK   = 0x3B2414;
    static final int MUTED = 0x8C6E4A;
    static final int GREEN = 0x9BE07A;
    static final int RED   = 0xF08080;

    /** Cases de la grille des listes (rangées 1 à 4). */
    static final int GRID = 36;

    ValmontMenu(UiKit kit, Player player) {
        super(kit, player);
    }

    /** Bandeau du nom : « NOM · RÔLE » s'il tient, sinon le nom seul (raccourci au besoin). */
    void nameplate(UiCanvas c, String name, String role) {
        String full = role == null || role.isBlank() ? name : name + " · " + role;
        String text = c.width(full, true) <= NAME_MAX ? full : c.fit(name, NAME_MAX, true);
        c.textCentered(text, NAME_CENTER, NAME_Y, GOLD, true);
    }

    void close(UiCanvas c) {
        c.sprite("ic_close", sx(8), sy(0));
        zone(8, 0, 1, 1, "§c✖ Fermer", List.of(), t -> player.closeInventory());
    }

    void back(UiCanvas c, int col, String label, Runnable action) {
        c.sprite("ic_back", sx(col), sy(0));
        zone(col, 0, 1, 1, "§7← " + label, List.of(), t -> action.run());
    }

    void info(UiCanvas c, int col, String title, List<String> lore) {
        c.sprite("ic_info", sx(col), sy(0));
        zone(col, 0, 1, 1, title, lore, null);
    }

    /** Barre de la rangée 5 : remplissage (0 à 1) et légende centrée. */
    void bar(UiCanvas c, double fraction, boolean full, String text) {
        int n = (int) Math.round(Math.max(0, Math.min(1, fraction)) * BAR_SEGMENTS);
        c.repeat(full ? "seg_xp_max" : "seg_xp", BAR_X, BAR_Y, n);
        if (text != null && !text.isBlank()) {
            c.textCentered(c.fit(text, 134, true), BAR_CENTER, BAR_TEXT_Y, CREAM, true);
        }
    }

    /** Flèches de pagination (rangée 5, colonnes 0 et 8). */
    void pager(UiCanvas c, int page, int pages, IntConsumer goTo) {
        boolean prev = page > 0, next = page < pages - 1;
        c.sprite(prev ? "ic_prev" : "ic_prev_off", sx(0), sy(5));
        c.sprite(next ? "ic_next" : "ic_next_off", sx(8), sy(5));
        if (prev) zone(0, 5, 1, 1, "§7◀ Page précédente", List.of("§8Page " + page + " / " + pages), t -> goTo.accept(page - 1));
        if (next) zone(8, 5, 1, 1, "§7Page suivante ▶", List.of("§8Page " + (page + 2) + " / " + pages), t -> goTo.accept(page + 1));
    }

    /** Plaque du pied de page : pièce + texte (bourse du joueur, compte…). */
    void footer(UiCanvas c, boolean coin, String text) {
        int x = sx(1) + 6;
        if (coin) {
            c.sprite("coin", x, sy(5) + 5);
            x += 12;
        }
        c.text(c.fit(text, sx(8) - x - 4, true), x, rowText(5), GOLD, true);
    }

    /** Montant lisible : 1250 → « 1 250 ». */
    static String fmt(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    static String plain(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    /** Nombre de pages pour {@code count} éléments de grille. */
    static int pages(int count) {
        return Math.max(1, (count + GRID - 1) / GRID);
    }

    static int clampPage(int page, int count) {
        return Math.max(0, Math.min(page, pages(count) - 1));
    }

    long balance() {
        return kit.economy() == null ? 0 : (long) Math.floor(kit.economy().getBalance(player));
    }
}
