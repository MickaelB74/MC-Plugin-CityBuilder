package com.citycore.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

/**
 * Compose le titre d'un menu « Valmont » : fond, images et textes posés au pixel près.
 *
 * Coordonnées = pixels du menu (coin supérieur gauche du coffre = 0,0 ; cases à
 * x = 7 + 18 × colonne, y = 17 + 18 × rangée). Le texte du titre commence à x = 8 : un curseur
 * suit la position courante et des caractères d'espacement le déplacent.
 */
public final class UiCanvas {

    private final UiAtlas atlas;
    private final TextComponent.Builder root = Component.text();
    private int cursor;

    /** Titre de coffre : le texte commence à x = 8. */
    UiCanvas(UiAtlas atlas) {
        this(atlas, 8);
    }

    /** @param originX position de départ du texte (0 pour une ligne libre, ex. liste des joueurs) */
    UiCanvas(UiAtlas atlas, int originX) {
        this.atlas  = atlas;
        this.cursor = originX;
    }

    /* =========================
       IMAGES
       ========================= */

    /** Fond de fenêtre (184 x 230, débordant de 4 px autour du coffre). */
    public UiCanvas background(String name) {
        UiAtlas.Sprite s = atlas.sprite(name);
        moveTo(-4);
        root.append(Component.text(String.valueOf(s.ch())).font(UiAtlas.UI_FONT).color(NamedTextColor.WHITE));
        cursor += s.advance();
        return this;
    }

    /** Image dont le coin supérieur gauche tombe en (x, y). */
    public UiCanvas sprite(String name, int x, int y) {
        UiAtlas.Sprite s = atlas.sprite(name);
        Key font = atlas.spriteFont(name, y);
        moveTo(x);
        root.append(Component.text(String.valueOf(s.ch())).font(font).color(NamedTextColor.WHITE));
        cursor += s.advance();
        return this;
    }

    /**
     * Répète une image côte à côte (segments de barre) : {@code count} exemplaires accolés.
     * Chaque image avance de sa largeur + 1 : on recule d'un pixel entre deux.
     */
    public UiCanvas repeat(String name, int x, int y, int count) {
        if (count <= 0) return this;
        UiAtlas.Sprite s = atlas.sprite(name);
        Key font = atlas.spriteFont(name, y);
        moveTo(x);
        int overlap = s.advance() - s.width();
        String unit = s.ch() + atlas.shift(-overlap);
        root.append(Component.text(unit.repeat(count)).font(font).color(NamedTextColor.WHITE));
        cursor += count * s.width();
        return this;
    }

    /**
     * Pixels colorés d'une même rangée (portraits) : une image blanche teintée par couleur.
     * {@code colors[i] == 0} = pixel transparent.
     */
    public UiCanvas pixelRow(String pixel, int x, int y, int[] colors) {
        UiAtlas.Sprite s = atlas.sprite(pixel);
        Key font = atlas.spriteFont(pixel, y);
        String step = s.ch() + atlas.shift(s.width() - s.advance());
        int i = 0;
        while (i < colors.length) {
            if (colors[i] == 0) { i++; continue; }
            int color = colors[i];
            int run = 1;
            while (i + run < colors.length && colors[i + run] == color) run++;
            moveTo(x + i * s.width());
            root.append(Component.text(step.repeat(run)).font(font).color(TextColor.color(color & 0xFFFFFF)));
            cursor += run * s.width();
            i += run;
        }
        return this;
    }

    /* =========================
       TEXTE (police pixel, majuscules)
       ========================= */

    /**
     * Texte aligné à gauche ; y = haut de la cellule (la capitale commence 2 px plus bas).
     *
     * Police ombrée : la colonne d'ombre compte dans la largeur du glyphe (avance = lettre + 2) ;
     * on recule d'un pixel après chaque lettre pour garder l'espacement de la police plate.
     */
    public UiCanvas text(String text, int x, int y, int rgb, boolean shadow) {
        String s = normalize(text);
        if (s.isEmpty()) return this;
        moveTo(x);
        String content = s;
        if (shadow) {
            String back = atlas.shift(-1);
            StringBuilder sb = new StringBuilder(s.length() * 2);
            for (int i = 0; i < s.length(); i++) {
                sb.append(s.charAt(i));
                if (s.charAt(i) != ' ') sb.append(back);
            }
            content = sb.toString();
        }
        root.append(Component.text(content).font(atlas.textFont(y, shadow)).color(TextColor.color(rgb)));
        cursor += width(s, shadow);
        return this;
    }

    public UiCanvas textCentered(String text, int centerX, int y, int rgb, boolean shadow) {
        String s = normalize(text);
        return text(s, centerX - width(s, shadow) / 2, y, rgb, shadow);
    }

    public UiCanvas textRight(String text, int rightX, int y, int rgb, boolean shadow) {
        String s = normalize(text);
        return text(s, rightX - width(s, shadow), y, rgb, shadow);
    }

    /* =========================
       PETITE POLICE (HUD compact)
       ========================= */

    /** Petit texte aligné à gauche ; y = haut de la cellule (la capitale commence 2 px plus bas). */
    public UiCanvas small(String text, int x, int y, int rgb) {
        String s = normalizeSmall(text);
        if (s.isEmpty()) return this;
        moveTo(x);
        root.append(Component.text(s).font(atlas.smallFont(y)).color(TextColor.color(rgb)));
        cursor += smallWidth(s);
        return this;
    }

    public UiCanvas smallCentered(String text, int centerX, int y, int rgb) {
        String s = normalizeSmall(text);
        return small(s, centerX - smallWidth(s) / 2, y, rgb);
    }

    public UiCanvas smallRight(String text, int rightX, int y, int rgb) {
        String s = normalizeSmall(text);
        return small(s, rightX - smallWidth(s), y, rgb);
    }

    public int smallWidth(String text) {
        String s = normalizeSmall(text);
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += atlas.smallAdvance(s.charAt(i));
        return w;
    }

    /** Raccourcit un petit texte (point final) pour qu'il tienne dans {@code maxWidth}. */
    public String fitSmall(String text, int maxWidth) {
        String s = normalizeSmall(text);
        if (smallWidth(s) <= maxWidth) return s;
        while (s.length() > 1 && smallWidth(s + ".") > maxWidth) s = s.substring(0, s.length() - 1).stripTrailing();
        return s + ".";
    }

    private String normalizeSmall(String text) {
        if (text == null) return "";
        String plain = text.replaceAll("§.", "").replace('\u2019', '\'').replace("\u2026", "...")
                .replace("\u0153", "\u0152").replace("\u00E6", "\u00C6");
        String upper = plain.toUpperCase(Locale.FRENCH);
        StringBuilder sb = new StringBuilder(upper.length());
        upper.codePoints().forEach(cp -> {
            if (cp < 0x10000 && atlas.supportsSmall((char) cp)) sb.append((char) cp);
            else if (Character.isLetterOrDigit(cp)) sb.append('?');
        });
        return sb.toString().replaceAll(" {2,}", " ").trim();
    }

    /** Largeur affichée d'un texte. */
    public int width(String text, boolean shadow) {
        String s = normalize(text);
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            w += atlas.advance(ch, shadow) - (shadow && ch != ' ' ? 1 : 0);
        }
        return w;
    }

    /** Raccourcit un texte (avec un point final) pour qu'il tienne dans {@code maxWidth}. */
    public String fit(String text, int maxWidth, boolean shadow) {
        String s = normalize(text);
        if (width(s, shadow) <= maxWidth) return s;
        while (s.length() > 1 && width(s + ".", shadow) > maxWidth) s = s.substring(0, s.length() - 1).stripTrailing();
        return s + ".";
    }

    /**
     * Majuscules françaises, codes couleur retirés. Les symboles absents de la police (émojis des
     * titres de quête…) sont retirés ; une lettre absente devient « ? ».
     */
    public String normalize(String text) {
        if (text == null) return "";
        String plain = text.replaceAll("§.", "").replace('’', '\'').replace("…", "...")
                .replace("œ", "Œ").replace("æ", "Æ");
        String upper = plain.toUpperCase(Locale.FRENCH);
        StringBuilder sb = new StringBuilder(upper.length());
        upper.codePoints().forEach(cp -> {
            if (cp < 0x10000 && atlas.supports((char) cp)) sb.append((char) cp);
            else if (Character.isLetterOrDigit(cp)) sb.append('?');
            // autres symboles : ignorés
        });
        return sb.toString().replaceAll(" {2,}", " ").trim();
    }

    /* =========================
       COMPOSITION
       ========================= */

    private void moveTo(int x) {
        int delta = x - cursor;
        if (delta != 0) root.append(Component.text(atlas.shift(delta)).font(UiAtlas.UI_FONT));
        cursor = x;
    }

    /** Termine la ligne à la position x (largeur totale de la ligne, pour un centrage exact). */
    public UiCanvas end(int x) {
        moveTo(x);
        return this;
    }

    public Component build() {
        return root.build();
    }
}
