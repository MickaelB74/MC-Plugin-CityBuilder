package com.citycore.ui;

import net.kyori.adventure.key.Key;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Registre des éléments graphiques du pack d'interface « Valmont ».
 *
 * Principe : chaque image du pack est un caractère d'une police. Minecraft place une image de
 * police verticalement selon son « ascent » (haut de l'image = ligne de texte + 7 - ascent) ;
 * le titre d'un coffre étant écrit à y = 6, une image dont le haut doit tomber à la hauteur Y
 * du menu reçoit ascent = 13 - Y. Une même image affichée à plusieurs hauteurs figure donc dans
 * plusieurs polices : une police par hauteur ({@code citycore:y35}, {@code citycore:y53}…).
 * Horizontalement, on se déplace avec des caractères d'espacement négatifs ou positifs.
 *
 * Les hauteurs utilisées sont déclarées au démarrage (voir {@link UiLayout#declare}) : le pack
 * ne contient que les combinaisons utiles.
 */
public final class UiAtlas {

    public static final String NS = "citycore";
    /** Police des fonds de fenêtre et des espacements. */
    public static final Key UI_FONT = Key.key(NS, "ui");
    /** Hauteur (en pixels) des cellules de la police pixel. */
    public static final int TEXT_CELL = 12;
    /** Petite police (capitales 3 x 5) : cellules de 6 x 8. */
    public static final int SMALL_CELL_W = 6, SMALL_CELL_H = 8;
    /** Au-delà, Minecraft affiche un caractère manquant à la place de l'image. */
    public static final int MAX_GLYPH = 256;
    /** Le titre d'un coffre est dessiné à y = 6 : ascent = 13 - hauteur voulue. */
    public static final int ASCENT_ORIGIN = 13;

    /** Image du pack : caractère attribué, dimensions, avance (largeur réelle + 1). */
    public record Sprite(String name, char ch, int width, int height, int advance) {}

    private final Map<String, Sprite> sprites = new LinkedHashMap<>();
    private final Map<String, BufferedImage> images = new HashMap<>();
    /** hauteur → images déclarées à cette hauteur */
    private final Map<Integer, Set<String>> spritesAtY = new TreeMap<>();
    private final Set<Integer> flatTextY   = new TreeSet<>();
    private final Set<Integer> shadowTextY = new TreeSet<>();
    private final Set<Integer> smallTextY  = new TreeSet<>();
    private final Set<String>  backgrounds = new TreeSet<>();

    // Police pixel : lignes de 16 caractères + avances (texte plat / avec ombre)
    private List<String> fontRows = List.of();
    private final Map<Character, Integer> flatAdvance   = new HashMap<>();
    private final Map<Character, Integer> shadowAdvance = new HashMap<>();
    private List<String> smallRows = List.of();
    private final Map<Character, Integer> smallAdvance  = new HashMap<>();

    // Espacements : 2^0 … 2^10, négatifs puis positifs
    static final int SHIFT_BITS = 11;
    private static final char SHIFT_NEG = '';   //  = -1,  = -2,  = -4…
    private static final char SHIFT_POS = '';   //  = +1,  = +2…

    /* =========================
       ENREGISTREMENT (construction du pack)
       ========================= */

    /** Ajoute une image ; son caractère est attribué dans l'ordre d'enregistrement. */
    void register(String name, BufferedImage image) {
        if (sprites.containsKey(name)) return;
        if (image.getWidth() > MAX_GLYPH || image.getHeight() > MAX_GLYPH) {
            throw new IllegalArgumentException("Image d'interface « " + name + " » : " + image.getWidth() + "x"
                    + image.getHeight() + " dépasse " + MAX_GLYPH + " px (limite de Minecraft)");
        }
        char ch = (char) (0xE000 + sprites.size());
        int actual = 0;
        for (int x = image.getWidth() - 1; x >= 0 && actual == 0; x--) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) { actual = x + 1; break; }
            }
        }
        sprites.put(name, new Sprite(name, ch, image.getWidth(), image.getHeight(), actual + 1));
        images.put(name, image);
    }

    /** Police pixel : planches plate et ombrée (16 colonnes de cellules 8 x 12). */
    void registerFont(List<String> rows, BufferedImage flat, BufferedImage shadow) {
        this.fontRows = rows;
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r);
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                flatAdvance.put(ch, cellAdvance(flat, c, r));
                shadowAdvance.put(ch, cellAdvance(shadow, c, r));
            }
        }
    }

    /** Petite police (planche de 16 colonnes de cellules 6 x 8). */
    void registerSmallFont(List<String> rows, BufferedImage sheet) {
        this.smallRows = rows;
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r);
            for (int c = 0; c < row.length(); c++) {
                smallAdvance.put(row.charAt(c), cellAdvance(sheet, c, r, SMALL_CELL_W, SMALL_CELL_H));
            }
        }
    }

    private static int cellAdvance(BufferedImage sheet, int col, int row) {
        return cellAdvance(sheet, col, row, 8, TEXT_CELL);
    }

    private static int cellAdvance(BufferedImage sheet, int col, int row, int cellW, int cellH) {
        int x0 = col * cellW, y0 = row * cellH;
        for (int x = cellW - 1; x >= 0; x--) {
            for (int y = 0; y < cellH; y++) {
                if ((sheet.getRGB(x0 + x, y0 + y) >>> 24) != 0) return x + 2; // largeur réelle + 1
            }
        }
        return 1;
    }

    /* =========================
       DÉCLARATIONS (ce que les écrans affichent, et où)
       ========================= */

    public void background(String name) { backgrounds.add(name); }

    public void sprite(String name, int... ys) {
        for (int y : ys) spritesAtY.computeIfAbsent(y, k -> new TreeSet<>()).add(name);
    }

    public void flatText(int... ys)   { for (int y : ys) flatTextY.add(y); }

    public void shadowText(int... ys) { for (int y : ys) shadowTextY.add(y); }

    public void smallText(int... ys)  { for (int y : ys) smallTextY.add(y); }

    /* =========================
       LECTURE
       ========================= */

    public Sprite sprite(String name) {
        Sprite s = sprites.get(name);
        if (s == null) throw new IllegalArgumentException("Image d'interface inconnue : " + name);
        return s;
    }

    public boolean has(String name) { return sprites.containsKey(name); }

    BufferedImage image(String name) { return images.get(name); }

    Map<String, Sprite> sprites() { return Collections.unmodifiableMap(sprites); }

    Set<String> backgrounds() { return backgrounds; }

    Map<Integer, Set<String>> spritesAtY() { return spritesAtY; }

    Set<Integer> flatTextY() { return flatTextY; }

    Set<Integer> shadowTextY() { return shadowTextY; }

    List<String> fontRows() { return fontRows; }

    Set<Integer> smallTextY() { return smallTextY; }

    List<String> smallRows() { return smallRows; }

    /** Police de la petite police à la hauteur y. */
    public Key smallFont(int y) {
        if (!smallTextY.contains(y)) throw new IllegalStateException("Petit texte non déclaré à y=" + y);
        return Key.key(NS, "t" + y);
    }

    public int smallAdvance(char c) {
        if (c == ' ') return 4;
        Integer a = smallAdvance.get(c);
        return a == null ? 0 : a;
    }

    public boolean supportsSmall(char c) {
        return c == ' ' || smallAdvance.containsKey(c);
    }

    /** Police où une image déclarée s'affiche à la hauteur y. */
    public Key spriteFont(String name, int y) {
        Set<String> at = spritesAtY.get(y);
        if (at == null || !at.contains(name)) {
            throw new IllegalStateException("Image « " + name + " » non déclarée à y=" + y);
        }
        return Key.key(NS, "y" + y);
    }

    /** Police du texte à la hauteur y (cellule de 12 px dont la capitale commence 2 px plus bas). */
    public Key textFont(int y, boolean shadow) {
        if (!(shadow ? shadowTextY : flatTextY).contains(y)) {
            throw new IllegalStateException("Texte " + (shadow ? "ombré" : "plat") + " non déclaré à y=" + y);
        }
        return Key.key(NS, (shadow ? "s" : "y") + y);
    }

    /** Avance d'un caractère de la police pixel (0 s'il n'existe pas). */
    public int advance(char c, boolean shadow) {
        if (c == ' ') return 4;
        Integer a = (shadow ? shadowAdvance : flatAdvance).get(c);
        return a == null ? 0 : a;
    }

    public boolean supports(char c) {
        return c == ' ' || flatAdvance.containsKey(c);
    }

    /* =========================
       ESPACEMENTS
       ========================= */

    /** Caractères dont les avances additionnées valent {@code pixels}. */
    public String shift(int pixels) {
        if (pixels == 0) return "";
        StringBuilder sb = new StringBuilder();
        int abs = Math.abs(pixels);
        char base = pixels < 0 ? SHIFT_NEG : SHIFT_POS;
        for (int bit = SHIFT_BITS - 1; bit >= 0; bit--) {
            int value = 1 << bit;
            while (abs >= value) {
                sb.append((char) (base + bit + 1));
                abs -= value;
            }
        }
        return sb.toString();
    }

    /** Table des espacements pour le fournisseur « space » des polices. */
    static Map<String, Integer> shiftAdvances() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int bit = 0; bit < SHIFT_BITS; bit++) {
            map.put(String.valueOf((char) (SHIFT_NEG + bit + 1)), -(1 << bit));
            map.put(String.valueOf((char) (SHIFT_POS + bit + 1)), 1 << bit);
        }
        return map;
    }
}
