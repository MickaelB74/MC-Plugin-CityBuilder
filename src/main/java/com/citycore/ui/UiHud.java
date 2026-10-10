package com.citycore.ui;

import java.util.List;

import static com.citycore.ui.UiLayout.*;

/**
 * Panneau compact « Valmont » : bandeau doré titré, lignes de 8 px en petite police, bas.
 * Sert au panneau des quêtes (titre d'une barre de boss) et aux panneaux de la liste des joueurs.
 *
 * Le jeu dessine ces lignes avec une ombre : l'ombre du texte passe sous les pièces de fond
 * (opaques) et ne se voit pas ; seul le panneau entier projette une ombre d'un pixel.
 */
public final class UiHud {

    // Couleurs des textes (petite police blanche teintée)
    public static final int INK    = 0x3B2414;
    public static final int CREAM  = 0xF6EBCD;
    public static final int GOLD   = 0xF1CF5E;
    public static final int MUTED  = 0xC9B48A;
    public static final int GREEN  = 0x9BE07A;
    public static final int PURPLE = 0xC09AE8;

    /** HEAD = ligne d'en-tête (bois clair), BAR = ligne avec barre, PLAIN = ligne simple. */
    public enum Kind { HEAD, BAR, PLAIN }

    /**
     * Ligne du panneau : texte à gauche, valeur à droite (facultative), barre pour les lignes BAR.
     *
     * @param gold barre dorée (sinon verte)
     */
    public record Row(Kind kind, String text, int color, String right, int rightColor,
                      double fraction, boolean gold) {

        public static Row head(String text, int color) {
            return new Row(Kind.HEAD, text, color, null, 0, 0, false);
        }

        public static Row plain(String text, int color) {
            return new Row(Kind.PLAIN, text, color, null, 0, 0, false);
        }

        /** Libellé discret à gauche, valeur à droite. */
        public static Row value(String label, String value, int valueColor) {
            return new Row(Kind.PLAIN, label, MUTED, value, valueColor, 0, false);
        }

        public static Row bar(String text, int color, String right, int rightColor, double fraction, boolean gold) {
            return new Row(Kind.BAR, text, color, right, rightColor, fraction, gold);
        }
    }

    private UiHud() {}

    /**
     * Dessine un panneau.
     *
     * @param x      bord gauche (repère de la ligne de texte)
     * @param top    haut du bandeau (repère local, voir {@link UiLayout#hudRowTop})
     * @param offset écart entre une hauteur locale et la police qui l'affiche
     * @param title  titre du bandeau (null = bandeau vide)
     */
    public static void panel(UiCanvas c, int x, int top, int offset, String title, List<Row> rows, int maxRows) {
        int n = Math.min(rows.size(), maxRows);

        // Pièces de fond d'abord : les textes et barres passent par-dessus
        c.sprite("hud_top", x, top + offset);
        for (int r = 0; r < n; r++) {
            String piece = switch (rows.get(r).kind()) {
                case HEAD  -> "hud_head";
                case BAR   -> "hud_row";
                case PLAIN -> "hud_plain";
            };
            c.sprite(piece, x, hudRowTop(top, r) + offset);
        }
        c.sprite("hud_bottom", x, hudRowTop(top, n) + offset);

        if (title != null && !title.isBlank()) {
            c.smallCentered(c.fitSmall(title, HUD_W - 12), x + HUD_W / 2, top + HUD_TITLE_CELL + offset, INK);
        }
        // Zone de texte : de x + 5 au dernier pixel x + 78 (la valeur à droite y finit)
        int left = x + HUD_PAD, right = x + HUD_W - HUD_PAD + 1;
        for (int r = 0; r < n; r++) {
            Row row = rows.get(r);
            int y = hudRowTop(top, r) + offset;
            int rightWidth = row.right() == null ? 0 : c.smallWidth(row.right());
            int room = right - left - rightWidth - (rightWidth > 0 ? 3 : 0);
            c.small(c.fitSmall(row.text(), room), left, y, row.color());
            if (rightWidth > 0) c.smallRight(row.right(), right, y, row.rightColor());
            if (row.kind() == Kind.BAR) {
                int seg = (int) Math.round(Math.max(0, Math.min(1, row.fraction())) * HUD_SEGMENTS);
                c.repeat(row.gold() ? "seg_hud_gold" : "seg_hud", left, y + 7, seg);
            }
        }
    }

    /** Hauteur totale d'un panneau de n lignes. */
    public static int height(int rows) {
        return HUD_TOP_H + HUD_ROW_H * rows + HUD_BOTTOM_H;
    }
}
