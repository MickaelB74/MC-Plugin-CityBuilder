// Générateur du pixel art des interfaces « Valmont » (CityCore).
//
// Usage (depuis la racine du projet) :
//     java tools/ui-art/ValmontArt.java .
//
// Produit :
//   - src/main/resources/pack/assets/citycore/textures/ui/*.png : éléments du pack d'interface
//   - src/main/resources/ui/font_chars.txt                       : ordre des caractères de la police
//   - docs/ui-previews/*.png                                      : aperçus des écrans (x3)
//
// Toutes les images sont dessinées pixel par pixel (aucune ressource Mojang copiée).
// Les positions reprennent la géométrie d'un coffre de 6 rangées (176 x 222) : les cases sont
// à x = 7 + 18 * colonne, y = 17 + 18 * rangée (cadres de 18 px).

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ValmontArt {

    /* =========================================================
       PALETTE
       ========================================================= */

    static final int T   = 0x00000000;
    static final int OUT = 0xFF1E120A;                        // contour principal
    // Bois (du plus sombre au plus clair)
    static final int W0 = 0xFF3A2312, W1 = 0xFF55341B, W2 = 0xFF6E4524, W3 = 0xFF8B5A2F, W4 = 0xFFA9743F;
    // Parchemin
    static final int P0 = 0xFFA88A5A, P1 = 0xFFC9AE7C, P2 = 0xFFDCC594, P3 = 0xFFEBD9AE, P4 = 0xFFF6EBCD;
    // Or
    static final int G0 = 0xFF6A4510, G1 = 0xFFA87A22, G2 = 0xFFD6A53A, G3 = 0xFFF1CF5E, G4 = 0xFFFFF0A8;
    // Fer
    static final int S0 = 0xFF2B2B33, S1 = 0xFF45454F, S2 = 0xFF63636E, S3 = 0xFF878792, S4 = 0xFFB0B0BA;
    /** Couleur exacte du libellé « Inventaire » de Minecraft : posé sur ce fond, il disparaît. */
    static final int LABEL = 0xFF404040;
    static final int INK   = 0xFF3B2414;

    // Schémas de boutons : contour, sombre, base, clair, reflet
    static final int[] GREEN  = {0xFF14330F, 0xFF2E6B22, 0xFF45922F, 0xFF6DBB45, 0xFFA5E07A};
    static final int[] BLUE   = {0xFF111F42, 0xFF27458A, 0xFF3866BA, 0xFF5B8FDE, 0xFF9CC3F5};
    static final int[] AMBER  = {0xFF3F1F06, 0xFF8A4A14, 0xFFB86A22, 0xFFE09440, 0xFFF7C27A};
    static final int[] PURPLE = {0xFF260F36, 0xFF55267A, 0xFF7840A6, 0xFFA068CF, 0xFFCDA5EE};
    static final int[] RED    = {0xFF3D0B0B, 0xFF861E1E, 0xFFB43030, 0xFFDE5050, 0xFFF59A9A};
    static final int[] GOLD   = {0xFF4A3006, 0xFF9A6A14, 0xFFC99A2A, 0xFFEFC452, 0xFFFFEDA0};
    static final int[] TEAL   = {0xFF0B3030, 0xFF1E6A66, 0xFF2E948C, 0xFF52BDB2, 0xFF98E6DC};
    static final int[] GRAY   = {0xFF222227, 0xFF4A4A52, 0xFF6A6A74, 0xFF8E8E98, 0xFFB8B8C2};
    static final int[] BROWN  = {0xFF24150B, 0xFF4E3018, 0xFF6E4524, 0xFF8E5E32, 0xFFB8864E};

    /** Couleurs des icônes (une lettre = une couleur). */
    static final Map<Character, Integer> PAL = new HashMap<>();
    static {
        PAL.put('k', OUT);        PAL.put('w', 0xFFFFFFFF); PAL.put('W', 0xFFD8D8D8);
        PAL.put('p', P4);         PAL.put('P', P2);         PAL.put('q', P0);
        PAL.put('y', G4);         PAL.put('Y', G3);         PAL.put('o', G2);  PAL.put('O', G1);
        PAL.put('g', 0xFF7CC45A); PAL.put('G', 0xFF3E7A2C);
        PAL.put('b', 0xFF6E9BE0); PAL.put('B', 0xFF2E5AA8);
        PAL.put('r', 0xFFE05050); PAL.put('R', 0xFF9A2424);
        PAL.put('n', W4);         PAL.put('N', W3);         PAL.put('m', W1);
        PAL.put('s', S4);         PAL.put('S', S2);         PAL.put('d', S1);
        PAL.put('c', 0xFF8FE3F0); PAL.put('v', 0xFFC09AE8); PAL.put('V', 0xFF6A3D9A);
        PAL.put('l', 0xFFFFE07A); PAL.put('f', 0xFFE8B080); PAL.put('F', 0xFFB87850);
    }

    /* =========================================================
       PRIMITIVES
       ========================================================= */

    static BufferedImage img(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    static void px(BufferedImage im, int x, int y, int c) {
        if (x < 0 || y < 0 || x >= im.getWidth() || y >= im.getHeight()) return;
        if ((c >>> 24) == 0) return;
        im.setRGB(x, y, c);
    }

    static void clear(BufferedImage im, int x, int y) {
        if (x < 0 || y < 0 || x >= im.getWidth() || y >= im.getHeight()) return;
        im.setRGB(x, y, 0);
    }

    static void fill(BufferedImage im, int x, int y, int w, int h, int c) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) px(im, i, j, c);
    }

    static void rect(BufferedImage im, int x, int y, int w, int h, int c) {
        for (int i = x; i < x + w; i++) { px(im, i, y, c); px(im, i, y + h - 1, c); }
        for (int j = y; j < y + h; j++) { px(im, x, j, c); px(im, x + w - 1, j, c); }
    }

    static void blit(BufferedImage dst, BufferedImage src, int x, int y) {
        for (int j = 0; j < src.getHeight(); j++)
            for (int i = 0; i < src.getWidth(); i++) px(dst, x + i, y + j, src.getRGB(i, j));
    }

    /** Hachage déterministe : même dessin à chaque génération. */
    static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    static double rnd(int x, int y, int seed) {
        return (hash(x, y, seed) & 0xFFFF) / 65535.0;
    }

    /** Bruit doux (interpolation d'une grille grossière) pour les taches du parchemin. */
    static double smooth(int x, int y, int cell, int seed) {
        int gx = Math.floorDiv(x, cell), gy = Math.floorDiv(y, cell);
        double fx = (x - gx * cell) / (double) cell, fy = (y - gy * cell) / (double) cell;
        double a = rnd(gx, gy, seed), b = rnd(gx + 1, gy, seed);
        double c = rnd(gx, gy + 1, seed), d = rnd(gx + 1, gy + 1, seed);
        double top = a + (b - a) * fx, bot = c + (d - c) * fx;
        return top + (bot - top) * fy;
    }

    /* =========================================================
       MATIÈRES
       ========================================================= */

    /** Parchemin vieilli : grain, taches douces, bords plus sombres. */
    static void parchment(BufferedImage im, int x, int y, int w, int h, int seed) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                double stain = smooth(x + i, y + j, 9, seed);
                double grain = rnd(x + i, y + j, seed + 7);
                int c = P3;
                if (stain < 0.28) c = P2;
                if (grain < 0.06) c = P2;
                if (grain > 0.965) c = P4;
                if (stain > 0.86 && grain > 0.5) c = P4;
                int edge = Math.min(Math.min(i, j), Math.min(w - 1 - i, h - 1 - j));
                if (edge == 0) c = P1;
                else if (edge == 1 && grain < 0.5) c = P2;
                px(im, x + i, y + j, c);
            }
        }
    }

    /** Planches horizontales avec veinage et nœuds. */
    static void wood(BufferedImage im, int x, int y, int w, int h, int seed, boolean dark) {
        int base = dark ? W1 : W2, grain = dark ? W0 : W1, light = dark ? W2 : W3;
        int plank = 6;
        for (int j = 0; j < h; j++) {
            int row = (y + j) / plank;
            int inPlank = (y + j) % plank;
            int offset = hash(row, 3, seed) & 63;
            for (int i = 0; i < w; i++) {
                int c = base;
                if (inPlank == 0) c = dark ? OUT : W0;              // joint entre planches
                else if (inPlank == 1) c = light;                   // arête éclairée
                else {
                    double v = Math.sin((x + i + offset) * 0.35 + row * 1.7 + inPlank * 0.9);
                    if (v > 0.82) c = grain;
                    if (rnd(x + i, y + j, seed) < 0.05) c = grain;
                    // nœud du bois
                    int kx = (hash(row, 11, seed) & 127) % Math.max(1, w), ky = 3;
                    if (Math.abs(i - kx) <= 1 && inPlank == ky) c = W0;
                }
                px(im, x + i, y + j, c);
            }
        }
    }

    /** Case creusée (fond de parchemin sombre, ombre en haut à gauche). */
    static void slotWell(BufferedImage im, int x, int y) {
        fill(im, x + 1, y + 1, 16, 16, P1);
        for (int i = 0; i < 18; i++) { px(im, x + i, y, P0); px(im, x, y + i, P0); }
        for (int i = 0; i < 18; i++) { px(im, x + i, y + 17, P4); px(im, x + 17, y + i, P4); }
        for (int i = 1; i < 17; i++) { px(im, x + i, y + 1, P0); px(im, x + 1, y + i, P0); }
        px(im, x, y + 17, P2); px(im, x + 17, y, P2);
        // léger grain
        for (int j = 2; j < 16; j++) for (int i = 2; i < 16; i++)
            if (rnd(x + i, y + j, 5) < 0.12) px(im, x + i, y + j, P0 + 0x00080808);
    }

    /** Case de carte (liste) : petite fiche de parchemin clair. */
    static void cardWell(BufferedImage im, int x, int y, int seed) {
        fill(im, x + 1, y + 1, 16, 16, P3);
        for (int j = 1; j < 17; j++) for (int i = 1; i < 17; i++) {
            double g = rnd(x + i, y + j, seed);
            if (g < 0.08) px(im, x + i, y + j, P2);
            if (g > 0.97) px(im, x + i, y + j, P4);
        }
        rect(im, x, y, 18, 18, P0);
        px(im, x, y, T == 0 ? P1 : P1); px(im, x + 17, y, P1); px(im, x, y + 17, P1); px(im, x + 17, y + 17, P1);
        for (int i = 1; i < 17; i++) px(im, x + i, y + 16, P2);
    }

    /** Boîte biseautée (bouton) : reflet en haut, profondeur en bas. */
    static void bevel(BufferedImage im, int x, int y, int w, int h, int[] s) {
        fill(im, x, y, w, h, s[2]);
        rect(im, x, y, w, h, s[0]);
        for (int i = x + 1; i < x + w - 1; i++) { px(im, i, y + 1, s[4]); px(im, i, y + h - 2, s[1]); px(im, i, y + h - 3, s[1]); }
        for (int j = y + 2; j < y + h - 3; j++) { px(im, x + 1, j, s[3]); px(im, x + w - 2, j, s[1]); }
        // coins arrondis
        clear(im, x, y); clear(im, x + w - 1, y); clear(im, x, y + h - 1); clear(im, x + w - 1, y + h - 1);
        px(im, x + 1, y + 1, s[0]); px(im, x + w - 2, y + 1, s[0]);
        px(im, x + 1, y + h - 2, s[0]); px(im, x + w - 2, y + h - 2, s[0]);
    }

    /** Creux sombre (logement de bouton, plaque de texte). */
    static void recess(BufferedImage im, int x, int y, int w, int h, boolean wooden) {
        int base = wooden ? W0 : S1, top = OUT, bottom = wooden ? W2 : S3;
        fill(im, x, y, w, h, base);
        for (int i = x; i < x + w; i++) { px(im, i, y, top); px(im, i, y + h - 1, bottom); }
        for (int j = y; j < y + h; j++) { px(im, x, j, top); px(im, x + w - 1, j, bottom); }
        for (int i = x + 1; i < x + w - 1; i++) px(im, i, y + 1, wooden ? 0xFF2C1A0D : S0);
    }

    /** Rivet doré. */
    static void rivet(BufferedImage im, int x, int y) {
        px(im, x, y, G4); px(im, x + 1, y, G2); px(im, x, y + 1, G2); px(im, x + 1, y + 1, G0);
    }

    /** Équerre dorée dans un coin (dx/dy = sens vers l'intérieur). */
    static void goldCorner(BufferedImage im, int x, int y, int dx, int dy) {
        for (int k = 0; k < 7; k++) {
            px(im, x + k * dx, y, k < 6 ? G2 : G0);
            px(im, x, y + k * dy, k < 6 ? G2 : G0);
            if (k < 5) { px(im, x + k * dx, y + dy, G1); px(im, x + dx, y + k * dy, G1); }
        }
        px(im, x, y, G4);
        rivet(im, x + 2 * dx - (dx < 0 ? 1 : 0), y + 2 * dy - (dy < 0 ? 1 : 0));
    }

    /* =========================================================
       POLICE PIXEL (majuscules, chiffres, accents français)
       Cellule de 8 x 12 : accents lignes 0-1, capitales 2-8, jambages 9-10, ombre jusqu'à 11.
       ========================================================= */

    static final Map<Character, String[]> GLYPHS = new LinkedHashMap<>();
    /** Accents rangés à part : lignes -2 et -1 au-dessus de la capitale (ou jambage). */
    static final Map<Character, String[]> ACCENTS = new HashMap<>();
    static final Map<Character, String[]> DESCENDERS = new HashMap<>();

    static void g(char c, String... rows) { GLYPHS.put(c, rows); }

    static {
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####");
        g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('I', "###", ".#.", ".#.", ".#.", ".#.", ".#.", "###");
        g('J', "..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##..");
        g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        g('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#.");
        g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        g('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        g('1', ".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###");
        g('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        g('3', "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###.");
        g('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        g('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        g('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.");
        g('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        g('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        g('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..");
        g('.', ".", ".", ".", ".", ".", ".", "#");
        g(',', "..", "..", "..", "..", "..", ".#", ".#", "#.");
        g(':', ".", ".", "#", ".", ".", "#", ".");
        g(';', "..", "..", ".#", "..", "..", ".#", ".#", "#.");
        g('!', "#", "#", "#", "#", "#", ".", "#");
        g('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..");
        g('\'', "#", "#", ".", ".", ".", ".", ".");
        g('"', "#.#", "#.#", "...", "...", "...", "...", "...");
        g('-', "...", "...", "...", "###", "...", "...", "...");
        g('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        g('/', "....#", "....#", "...#.", "..#..", ".#...", "#....", "#....");
        g('%', "##..#", "##..#", "...#.", "..#..", ".#...", "#..##", "#..##");
        g('(', "..#", ".#.", "#..", "#..", "#..", ".#.", "..#");
        g(')', "#..", ".#.", "..#", "..#", "..#", ".#.", "#..");
        g('[', "##", "#.", "#.", "#.", "#.", "#.", "##");
        g(']', "##", ".#", ".#", ".#", ".#", ".#", "##");
        g('<', "...#", "..#.", ".#..", "#...", ".#..", "..#.", "...#");
        g('>', "#...", ".#..", "..#.", "...#", "..#.", ".#..", "#...");
        g('=', ".....", ".....", "#####", ".....", "#####", ".....", ".....");
        g('#', ".#.#.", ".#.#.", "#####", ".#.#.", "#####", ".#.#.", ".#.#.");
        g('&', ".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#");
        g('_', ".....", ".....", ".....", ".....", ".....", ".....", "#####");
        g('*', ".....", "#.#.#", ".###.", "#####", ".###.", "#.#.#", ".....");
        g('°', ".#.", "#.#", ".#.", "...", "...", "...", "...");
        g('×', ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", ".....");  // ×
        g('·', ".", ".", ".", "#", ".", ".", ".");                                  // ·
        g('•', "..", "..", "##", "##", "..", "..", "..");                           // •
        g('→', ".....", "..#..", "...#.", "#####", "...#.", "..#..", ".....");    // →
        g('←', ".....", "..#..", ".#...", "#####", ".#...", "..#..", ".....");    // ←
        g('★', "...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", ".#...#."); // ★
        g('✔', ".....", "....#", "...##", "#.##.", "###..", ".#...", ".....");    // ✔
        g('Œ', ".####", "#.#..", "#.#..", "#.###", "#.#..", "#.#..", ".####");    // Œ
        g('Æ', ".####", "#.#..", "#.#..", "#####", "#.#..", "#.#..", "#.###");    // Æ
        // Lettres accentuées (accent dessiné au-dessus de la capitale)
        accent('À', 'A', "grave");  accent('Â', 'A', "circ");  accent('Ä', 'A', "diaer");
        accent('È', 'E', "grave");  accent('É', 'E', "acute"); accent('Ê', 'E', "circ");
        accent('Ë', 'E', "diaer");  accent('Î', 'I', "circ");  accent('Ï', 'I', "diaer");
        accent('Ô', 'O', "circ");   accent('Ö', 'O', "diaer"); accent('Ù', 'U', "grave");
        accent('Û', 'U', "circ");   accent('Ü', 'U', "diaer"); accent('Ÿ', 'Y', "diaer");
        accent('Ç', 'C', "cedil");
    }

    static void accent(char c, char base, String kind) {
        String[] rows = GLYPHS.get(base);
        int w = rows[0].length();
        int mid = w / 2;
        char[] r0 = ".".repeat(w).toCharArray(), r1 = ".".repeat(w).toCharArray();
        switch (kind) {
            case "acute" -> { r0[Math.min(w - 1, mid + 1)] = '#'; r1[mid] = '#'; }
            case "grave" -> { r0[Math.max(0, mid - 1)] = '#'; r1[mid] = '#'; }
            case "circ"  -> { r0[mid] = '#'; r1[Math.max(0, mid - 1)] = '#'; r1[Math.min(w - 1, mid + 1)] = '#'; }
            case "diaer" -> { r1[Math.max(0, mid - 1)] = '#'; r1[Math.min(w - 1, mid + 1)] = '#'; if (w <= 3) { r1[0] = '#'; r1[w - 1] = '#'; } }
            case "cedil" -> { }
            default -> throw new IllegalArgumentException(kind);
        }
        GLYPHS.put(c, rows);
        if (kind.equals("cedil")) {
            char[] d0 = ".".repeat(w).toCharArray(), d1 = ".".repeat(w).toCharArray();
            d0[mid] = '#'; d1[Math.max(0, mid - 1)] = '#';
            DESCENDERS.put(c, new String[]{new String(d0), new String(d1)});
        } else {
            ACCENTS.put(c, new String[]{new String(r0), new String(r1)});
        }
    }

    /** Largeur d'un glyphe en pixels (sans espacement). */
    static int glyphWidth(char c) {
        if (c == ' ') return 3;
        String[] rows = GLYPHS.get(c);
        return rows == null ? 5 : rows[0].length();
    }

    /** Largeur d'un texte (1 px entre lettres, +1 pour l'ombre). */
    static int textWidth(String s, boolean shadow) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += glyphWidth(s.charAt(i)) + 1;
        return Math.max(0, w - 1) + (shadow ? 1 : 0);
    }

    /** Dessine un glyphe : (x, y) = coin supérieur gauche de la capitale. */
    static void glyph(BufferedImage im, char c, int x, int y, int color) {
        String[] rows = GLYPHS.get(c);
        if (rows == null) return;
        for (int j = 0; j < rows.length; j++)
            for (int i = 0; i < rows[j].length(); i++)
                if (rows[j].charAt(i) == '#') px(im, x + i, y + j, color);
        String[] acc = ACCENTS.get(c);
        if (acc != null) for (int j = 0; j < 2; j++)
            for (int i = 0; i < acc[j].length(); i++)
                if (acc[j].charAt(i) == '#') px(im, x + i, y - 2 + j, color);
        String[] desc = DESCENDERS.get(c);
        if (desc != null) for (int j = 0; j < 2; j++)
            for (int i = 0; i < desc[j].length(); i++)
                if (desc[j].charAt(i) == '#') px(im, x + i, y + 7 + j, color);
    }

    /** Texte avec ombre portée (1 px en bas à droite). y = haut des capitales. */
    static void text(BufferedImage im, String s, int x, int y, int color, int shadow) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (shadow != T) glyph(im, c, cx + 1, y + 1, shadow);
            glyph(im, c, cx, y, color);
            cx += glyphWidth(c) + 1;
        }
    }

    static void textCentered(BufferedImage im, String s, int cx, int y, int color, int shadow) {
        text(im, s, cx - textWidth(s, shadow != T) / 2, y, color, shadow);
    }

    /** Planche de police : 16 colonnes, cellules 8 x 12, glyphes blancs (+ ombre grise). */
    static BufferedImage fontSheet(List<Character> chars, boolean shadow) {
        int rows = (chars.size() + 15) / 16;
        BufferedImage im = img(16 * 8, rows * 12);
        for (int k = 0; k < chars.size(); k++) {
            int cx = (k % 16) * 8, cy = (k / 16) * 12;
            char c = chars.get(k);
            // L'ombre est grise : multipliée par la couleur du texte, elle en donne une teinte sombre
            if (shadow) glyph(im, c, cx + 1, cy + 3, 0xFF3A3A3A);
            glyph(im, c, cx, cy + 2, 0xFFFFFFFF);
        }
        return im;
    }

    /* =========================================================
       ICÔNES 12 x 12
       ========================================================= */

    static final Map<String, String[]> ICONS = new LinkedHashMap<>();

    static void icon(String name, String... rows) {
        for (String r : rows) if (r.length() != 12) throw new IllegalStateException(name + " : " + r);
        if (rows.length != 12) throw new IllegalStateException(name + " : " + rows.length + " lignes");
        ICONS.put(name, rows);
    }

    static {
        icon("coins",
                "............",
                "...kkkkkk...",
                "..kyyyyyyk..",
                "..kYoYYoYk..",
                "..kkkkkkkk..",
                ".kyyyyyyyyk.",
                ".kYYoYYoYYk.",
                ".kkkkkkkkkk.",
                "kyyyyyyyyyyk",
                "kYYoYYYYoYYk",
                "kOOOOOOOOOOk",
                "kkkkkkkkkkkk");
        icon("crate",
                "kkkkkkkkkkkk",
                "knnnnnnnnnnk",
                "knmmmmmmmmnk",
                "knmNNNNNNmnk",
                "knmNmNNmNmnk",
                "knmNNmmNNmnk",
                "knmNNmmNNmnk",
                "knmNmNNmNmnk",
                "knmNNNNNNmnk",
                "knmmmmmmmmnk",
                "knnnnnnnnnnk",
                "kkkkkkkkkkkk");
        icon("stall",
                "kkkkkkkkkkkk",
                "krrwwrrwwrrk",
                "kRRWWRRWWRRk",
                ".kkkkkkkkkk.",
                ".kNk....kNk.",
                ".kNk.yk.kNk.",
                ".kNkyYbBkNk.",
                ".kNkkkkkkNk.",
                ".knnnnnnnnk.",
                ".kmmmmmmmmk.",
                ".kNk....kNk.",
                ".kkk....kkk.");
        icon("scroll",
                ".kkkkkkkkkk.",
                "kPppppppppPk",
                ".kkkkkkkkkk.",
                "..kppppppk..",
                "..kpqqqqpk..",
                "..kppppppk..",
                "..kpqqqqpk..",
                "..kppppppk..",
                "..kpqqqppk..",
                ".kkkkkkkkkk.",
                "kPppppppppPk",
                ".kkkkkkkkkk.");
        icon("boots",
                "............",
                "...kkkk.....",
                "...knNk.....",
                "...knNk.....",
                "...knNk.....",
                "...knNkkkk..",
                "g..knNNNNNk.",
                ".gkknNNNNNNk",
                "g.kmmmmmmmmk",
                "..kkkkkkkkkk",
                "............",
                "............");
        icon("stop",
                "....kkkk....",
                "..kkrrrrkk..",
                ".krrrrrrrrk.",
                ".krrrrrrrrk.",
                "krrrrrrrrrrk",
                "krwwwwwwwwrk",
                "krwwwwwwwwrk",
                "krrrrrrrrrrk",
                ".kRrrrrrrRk.",
                ".kRRrrrrRRk.",
                "..kkRRRRkk..",
                "....kkkk....");
        icon("hammer",
                ".kkkkkkk....",
                "kssssssk....",
                "kSSSSSSkk...",
                ".kkkkkknk...",
                ".....knk....",
                "....knk.....",
                "...knk......",
                "..knk.......",
                ".knk........",
                "kmk.........",
                "kk..........",
                "............");
        icon("house",
                ".....kk.....",
                "....kRRk....",
                "...kRrrRk...",
                "..kRrrrrRk..",
                ".kRrrrrrrRk.",
                "kkkkkkkkkkkk",
                ".kppppppppk.",
                ".kpkkkpppPk.",
                ".kpkbkpkkPk.",
                ".kpkkkpkNPk.",
                ".kppppPkNPk.",
                ".kkkkkkkkkk.");
        icon("map",
                "kkkkkkkkkkkk",
                "kpPppPppPppk",
                "kpPgpPppPgpk",
                "kpPggPgpPggk",
                "kpPpgPggPgpk",
                "kpPppPpgPppk",
                "kpPrpPppPppk",
                "kpPrrPppPppk",
                "kpPppPrpPppk",
                "kpPppPrrPrrk",
                "kpPppPppPppk",
                "kkkkkkkkkkkk");
        icon("coinup",
                ".....kk.....",
                "....kggk....",
                "...kggggk...",
                "..kkkggkkk..",
                "....kggk....",
                "..kkkkkkkk..",
                ".kyyyyyyyyk.",
                "kyYYYYYYYYyk",
                "kYYoYYYYoYYk",
                "kOYYYYYYYYOk",
                ".kOOOOOOOOk.",
                "..kkkkkkkk..");
        icon("chest",
                "............",
                ".kkkkkkkkkk.",
                "knnnnnnnnnnk",
                "kmmmmmmmmmmk",
                "kkkkkkkkkkkk",
                "kyYyYyYyYyYk",
                "kNNNNkkNNNNk",
                "kNNNNkyNNNNk",
                "kNNNNNNNNNNk",
                "kmmmmmmmmmmk",
                "kNNNNNNNNNNk",
                "kkkkkkkkkkkk");
        icon("book",
                ".kkkkkkkkk..",
                "kRRRRRRRRRk.",
                "kRrrrrrrrRkk",
                "kRryyyyyrRpk",
                "kRrrrrrrrRpk",
                "kRryyyyrrRpk",
                "kRrrrrrrrRpk",
                "kRrrrrrrrRpk",
                "kRRRRRRRRRpk",
                "kkkkkkkkkkpk",
                ".kpppppppppk",
                "..kkkkkkkkk.");
        icon("walk",
                "....kkk.....",
                "....kfk.....",
                "....kkk.....",
                "...kBBBk....",
                "..kBkBkBk...",
                "..k.kBk.k...",
                "....kNk.....",
                "...kNkNk....",
                "..kNk.kNk...",
                "..kk...kk...",
                "g.......g...",
                ".g.....g....");
        icon("flag",
                "..kkkkkkk...",
                "..kNrrrrrk..",
                "..kNrrrrk...",
                "..kNrrrk....",
                "..kNkkk.....",
                "..kNk.......",
                "..kNk.......",
                "..kNk.......",
                "..kNk.......",
                ".kkNkk......",
                ".kmmmmk.....",
                ".kkkkkk.....");
        icon("cross",
                "............",
                ".kk......kk.",
                "kwwk....kwwk",
                ".kwwk..kwwk.",
                "..kwwkkwwk..",
                "...kwwwwk...",
                "...kwwwwk...",
                "..kwwkkwwk..",
                ".kwwk..kwwk.",
                "kwwk....kwwk",
                ".kk......kk.",
                "............");
        icon("left",
                "............",
                ".....kk.....",
                "....kwk.....",
                "...kwwkkkkk.",
                "..kwwwwwwwk.",
                ".kwwwwwwwwk.",
                "..kwwwwwwwk.",
                "...kwwkkkkk.",
                "....kwk.....",
                ".....kk.....",
                "............",
                "............");
        icon("right",
                "............",
                ".....kk.....",
                ".....kwk....",
                ".kkkkkwwk...",
                ".kwwwwwwwk..",
                ".kwwwwwwwwk.",
                ".kwwwwwwwk..",
                ".kkkkkwwk...",
                ".....kwk....",
                ".....kk.....",
                "............",
                "............");
        // Positions du panneau des quêtes : bord de l'écran à gauche, inventaire à droite
        icon("pos_edge",
                "............",
                "PPPPPPPPPPPP",
                "P...........",
                "PYY.....ssss",
                "PYo.....sSSS",
                "PYo.....sSSS",
                "PYo.....sSSS",
                "Poo.....sSSS",
                "P.......sSSS",
                "P.......sSSS",
                "PPPPPPPPPPPP",
                "............");
        icon("pos_margin",
                "............",
                "PPPPPPPPPPPP",
                "P...........",
                "P..YY...ssss",
                "P..Yo...sSSS",
                "P..Yo...sSSS",
                "P..Yo...sSSS",
                "P..oo...sSSS",
                "P.......sSSS",
                "P.......sSSS",
                "PPPPPPPPPPPP",
                "............");
        icon("pos_near",
                "............",
                "PPPPPPPPPPPP",
                "P...........",
                "P.....YYssss",
                "P.....YosSSS",
                "P.....YosSSS",
                "P.....YosSSS",
                "P.....oosSSS",
                "P.......sSSS",
                "P.......sSSS",
                "PPPPPPPPPPPP",
                "............");
        // Suivi accroché : liste des joueurs (Tab) avec ses panneaux, celui des quêtes sous la cité
        icon("pos_tab",
                "............",
                "PPPPPPPPPPPP",
                "P..........P",
                "P.YY....YY.P",
                "P.oo.ww.oo.P",
                "P.oo....oo.P",
                "P....ww....P",
                "P.YY.......P",
                "P.cc.ww....P",
                "P.cc.......P",
                "PPPPPPPPPPPP",
                "............");
        icon("info",
                "....kkkk....",
                "...kwwwwk...",
                "...kwwwwk...",
                "....kkkk....",
                "...kkkkk....",
                "...kwwwk....",
                "....kwwk....",
                "....kwwk....",
                "....kwwk....",
                "...kkwwkk...",
                "...kwwwwk...",
                "...kkkkkk...");
        icon("check",
                "............",
                "..........kk",
                ".........kwk",
                "........kwk.",
                ".kk....kwk..",
                "kwwk..kwwk..",
                ".kwwkkwwk...",
                "..kwwwwk....",
                "...kwwk.....",
                "....kk......",
                "............",
                "............");
        icon("star",
                ".....kk.....",
                ".....kyk....",
                "....kyYk....",
                "kkkkkyYkkkkk",
                "kyyyyYYYYYOk",
                ".kOYYYYYYOk.",
                "..kOYYYYOk..",
                "..kYYOOYYk..",
                ".kYYOkkOYYk.",
                ".kYOk..kOYk.",
                "kOOk....kOOk",
                "kkk......kkk");
        icon("hourglass",
                ".kkkkkkkkkk.",
                ".kNNNNNNNNk.",
                "..kyyyyyyk..",
                "..kYyyyyYk..",
                "...kYyyYk...",
                "....kYYk....",
                "....kpPk....",
                "...kpppPk...",
                "..kppYYpPk..",
                "..kpYYYYPk..",
                ".kNNNNNNNNk.",
                ".kkkkkkkkkk.");
        icon("clock",
                "...kkkkkk...",
                "..kYYYYYYk..",
                ".kYppppppYk.",
                "kYpppkkpppYk",
                "kYpppkkpppYk",
                "kYpppkkpppYk",
                "kYpppkkkkpYk",
                "kYppppppppYk",
                "kYppppppppYk",
                ".kYppppppYk.",
                "..kYYYYYYk..",
                "...kkkkkk...");
        icon("lock",
                "...kkkkkk...",
                "..kssssssk..",
                ".kskkkkkksk.",
                ".ksk....ksk.",
                ".ksk....ksk.",
                "kkkkkkkkkkkk",
                "kYYYYYYYYYYk",
                "kYoooookooYk",
                "kYooookkooYk",
                "kYoooookooYk",
                "kOOOOOOOOOOk",
                "kkkkkkkkkkkk");
        icon("quill",
                "..........kk",
                ".........kwk",
                "........kwWk",
                ".......kwWk.",
                "......kwWk..",
                ".....kwWk...",
                "....kwWk....",
                "...kwWk.....",
                "..kWWk......",
                "..kkk.......",
                ".kk.........",
                "kk..........");
        // Écu de la garde (bouton « Actions », écran de la garde)
        icon("shield",
                "kkkkkkkkkkkk",
                "kBBBBYYBBBBk",
                "kBbbbYYbbbBk",
                "kBbbbYYbbbBk",
                "kYYYYYYYYYYk",
                "kBbbbYYbbbBk",
                "kBbbbYYbbbBk",
                ".kBbbYYbbBk.",
                ".kBbbYYbbBk.",
                "..kBbYYbBk..",
                "...kBYYBk...",
                "....kkkk....");
        icon("crown",
                "............",
                "............",
                "k....k....k.",
                "kk..kYk..kk.",
                "kYk.kYk.kYk.",
                "kYYkYYYkYYk.",
                "kYYYYrYYYYk.",
                "kYYYrrrYYYk.",
                "kOOOOOOOOOk.",
                "kkkkkkkkkkk.",
                "............",
                "............");
    }

    static void drawIcon(BufferedImage im, String name, int x, int y) {
        String[] rows = ICONS.get(name);
        for (int j = 0; j < 12; j++)
            for (int i = 0; i < 12; i++) {
                char c = rows[j].charAt(i);
                if (c == '.') continue;
                Integer col = PAL.get(c);
                if (col == null) throw new IllegalStateException("couleur inconnue '" + c + "' dans " + name);
                px(im, x + i, y + j, col);
            }
    }

    /** Icône teintée (désactivée) : toutes les couleurs ramenées au gris. */
    static void drawIconGray(BufferedImage im, String name, int x, int y) {
        String[] rows = ICONS.get(name);
        for (int j = 0; j < 12; j++)
            for (int i = 0; i < 12; i++) {
                char c = rows[j].charAt(i);
                if (c == '.') continue;
                int col = PAL.get(c);
                int r = (col >> 16) & 255, gg = (col >> 8) & 255, b = col & 255;
                int l = (r * 3 + gg * 5 + b * 2) / 10;
                l = c == 'k' ? 0x20 : 0x50 + l / 3;
                px(im, x + i, y + j, 0xFF000000 | (l << 16) | (l << 8) | l);
            }
    }

    /* =========================================================
       COMPOSANTS
       ========================================================= */

    /** Gros bouton (90 x 18 par défaut) : marge transparente de 1 px, icône à gauche, libellé. */
    static BufferedImage button(int w, int h, int[] s, String icon, String label) {
        BufferedImage im = img(w, h);
        bevel(im, 1, 1, w - 2, h - 2, s);
        int textLeft = 5;
        if (icon != null) {
            // médaillon de l'icône
            fill(im, 3, 2, 14, 13, s[1]);
            if (s == GRAY) drawIconGray(im, icon, 4, 3); else drawIcon(im, icon, 4, 3);
            for (int j = 3; j < 15; j++) px(im, 17, j, s[0]);
            textLeft = 19;
        }
        int avail = w - textLeft - 4;
        int tw = textWidth(label, true);
        if (tw > avail) System.out.println("⚠ Libellé trop large (" + tw + " > " + avail + " px) : " + label);
        int tx = textLeft + Math.max(0, (avail - tw) / 2);
        text(im, label, tx, 5, s == GRAY ? 0xFFD0D0D8 : P4, s[0]);
        return im;
    }

    /** Petit bouton carré 18 x 18 avec une icône. */
    static BufferedImage iconButton(int[] s, String icon, boolean gray) {
        BufferedImage im = img(18, 18);
        bevel(im, 0, 0, 18, 18, s);
        if (gray) drawIconGray(im, icon, 3, 2); else drawIcon(im, icon, 3, 2);
        return im;
    }

    /** Bouton vertical 18 x 72 : icône en haut, lettres empilées. */
    static BufferedImage verticalButton(int[] s, String icon, String label, boolean gray) {
        BufferedImage im = img(18, 72);
        bevel(im, 1, 1, 16, 70, s);
        if (gray) drawIconGray(im, icon, 3, 3); else drawIcon(im, icon, 3, 3);
        int y = 18;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            int w = glyphWidth(c);
            glyph(im, c, 9 - (w + 1) / 2 + 1, y + 1, s[0]);
            glyph(im, c, 9 - (w + 1) / 2, y, gray ? 0xFFD0D0D8 : P4);
            y += 9;
        }
        return im;
    }

    /** Bannière d'en-tête (108 x 18) à queues d'aronde, libellé centré. */
    static BufferedImage ribbon(int[] s, String label) {
        int w = 108, h = 18;
        BufferedImage im = img(w, h);
        // corps
        for (int y = 2; y < 16; y++) for (int x = 6; x < w - 6; x++) px(im, x, y, s[2]);
        for (int x = 6; x < w - 6; x++) { px(im, x, 2, s[4]); px(im, x, 3, s[3]); px(im, x, 14, s[1]); px(im, x, 15, s[0]); }
        // queues d'aronde
        for (int y = 2; y < 16; y++) {
            int notch = Math.abs(y - 9) < 4 ? 3 - Math.abs(y - 9) : -1;
            for (int x = 0; x < 6; x++) {
                if (notch >= 0 && x < notch + 1) continue;
                int c = (x == 0 || y == 2 || y == 15) ? s[0] : s[1];
                px(im, x, y + (x < 3 ? 0 : 0), c);
                px(im, w - 1 - x, y, c);
            }
        }
        for (int y = 2; y < 16; y++) { px(im, 6, y, s[0]); px(im, w - 7, y, s[0]); }
        for (int x = 6; x < w - 6; x++) px(im, x, 1, s[0]);
        for (int x = 6; x < w - 6; x++) px(im, x, 16, s[0]);
        textCentered(im, label, w / 2, 6, P4, s[0]);
        return im;
    }

    /** Anneau d'état autour d'une case (18 x 18). */
    static BufferedImage ring(int[] s) {
        BufferedImage im = img(18, 18);
        rect(im, 0, 0, 18, 18, s[0]);
        rect(im, 1, 1, 16, 16, s[3]);
        px(im, 1, 1, s[4]); px(im, 16, 1, s[2]); px(im, 1, 16, s[2]); px(im, 16, 16, s[1]);
        clear(im, 0, 0); clear(im, 17, 0); clear(im, 0, 17); clear(im, 17, 17);
        return im;
    }

    /** Étoiles de niveau : n pleines sur 5 (39 x 7). */
    static BufferedImage stars(int n) {
        String[] core = {"..#..", ".###.", "#####", ".###.", ".#.#."};
        BufferedImage im = img(39, 7);
        for (int k = 0; k < 5; k++) {
            int ox = k * 8;
            boolean full = k < n;
            int fillC = full ? G3 : W2, hl = full ? G4 : W3, line = full ? G0 : OUT;
            // contour
            for (int j = 0; j < 5; j++) for (int i = 0; i < 5; i++) {
                if (core[j].charAt(i) != '#') continue;
                for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    int x = ox + 1 + i + di, y = 1 + j + dj;
                    int ci = i + di, cj = j + dj;
                    boolean inside = ci >= 0 && cj >= 0 && ci < 5 && cj < 5 && core[cj].charAt(ci) == '#';
                    if (!inside) px(im, x, y, line);
                }
            }
            for (int j = 0; j < 5; j++) for (int i = 0; i < 5; i++)
                if (core[j].charAt(i) == '#') px(im, ox + 1 + i, 1 + j, j <= 1 ? hl : fillC);
        }
        return im;
    }

    /** Plaque de bois sombre à liseré doré (90 x 18) pour un texte calculé. */
    static BufferedImage plate(int w) {
        BufferedImage im = img(w, 18);
        recess(im, 1, 1, w - 2, 16, true);
        rect(im, 2, 2, w - 4, 14, G1);
        for (int i = 3; i < w - 3; i++) px(im, i, 3, 0xFF2C1A0D);
        rivet(im, 3, 7); rivet(im, w - 5, 7);
        return im;
    }

    /** Segment de barre (2 px de large) en dégradé. */
    static BufferedImage segment(int h, int[] grad) {
        BufferedImage im = img(2, h);
        for (int y = 0; y < h; y++) {
            int c = grad[Math.min(grad.length - 1, y * grad.length / h)];
            px(im, 0, y, c); px(im, 1, y, c);
        }
        return im;
    }

    /** Pastille (8 x 8) : coche verte, sablier, croix… dessinée dans un coin de case. */
    static BufferedImage badge(int[] s, String kind) {
        BufferedImage im = img(9, 9);
        fill(im, 1, 1, 7, 7, s[2]);
        rect(im, 0, 0, 9, 9, s[0]);
        clear(im, 0, 0); clear(im, 8, 0); clear(im, 0, 8); clear(im, 8, 8);
        px(im, 1, 1, s[0]); px(im, 7, 1, s[0]); px(im, 1, 7, s[0]); px(im, 7, 7, s[0]);
        int w = 0xFFFFFFFF;
        switch (kind) {
            case "check" -> { px(im, 2, 4, w); px(im, 3, 5, w); px(im, 4, 6, w); px(im, 5, 5, w); px(im, 6, 4, w); px(im, 6, 3, w); px(im, 7, 2, w); }
            case "dot"   -> fill(im, 3, 3, 3, 3, w);
            case "cross" -> { for (int k = 2; k <= 6; k++) { px(im, k, k, w); px(im, 8 - k, k, w); } }
            case "bang"  -> { fill(im, 4, 2, 1, 3, w); px(im, 4, 6, w); }
            default -> throw new IllegalArgumentException(kind);
        }
        return im;
    }

    /* =========================================================
       DÉCORS DE PORTRAIT (64 x 64)
       ========================================================= */

    static int lerp(int a, int b, double t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static void vgrad(BufferedImage im, int x, int y, int w, int h, int top, int bottom, int seed) {
        for (int j = 0; j < h; j++) {
            double t = j / (double) Math.max(1, h - 1);
            for (int i = 0; i < w; i++) {
                // tramage ordonné : dégradé en bandes, façon pixel art
                double d = ((i + j * 2) % 4) / 8.0 - 0.2;
                double tt = Math.max(0, Math.min(1, t + d * 0.15));
                int steps = 6;
                double q = Math.round(tt * steps) / (double) steps;
                px(im, x + i, y + j, lerp(top, bottom, q));
            }
        }
    }

    static BufferedImage backdrop(String kind) {
        BufferedImage im = img(64, 64);
        switch (kind) {
            case "nature" -> {
                vgrad(im, 0, 0, 64, 40, 0xFF6FB6E8, 0xFFCDE8F2, 1);
                // nuages
                for (int[] c : new int[][]{{8, 8}, {40, 5}, {50, 14}}) {
                    fill(im, c[0], c[1], 10, 3, 0xFFF4FAFC); fill(im, c[0] + 2, c[1] - 2, 6, 2, 0xFFF4FAFC);
                }
                // collines lointaines
                for (int x = 0; x < 64; x++) {
                    int hh = 30 + (int) (4 * Math.sin(x * 0.15)) + (int) (2 * Math.sin(x * 0.4));
                    for (int y = hh; y < 64; y++) px(im, x, y, 0xFF7FA86A);
                    int h2 = 38 + (int) (3 * Math.sin(x * 0.22 + 2));
                    for (int y = h2; y < 64; y++) px(im, x, y, 0xFF5E9248);
                }
                // arbres
                for (int t = 0; t < 4; t++) {
                    int tx = 4 + t * 17 + (hash(t, 1, 9) & 3), ty = 26 + (hash(t, 2, 9) & 3);
                    fill(im, tx + 3, ty + 8, 2, 8, W2);
                    for (int j = 0; j < 10; j++) for (int i = -4; i <= 4; i++)
                        if (i * i + (j - 4) * (j - 4) <= 18) px(im, tx + 4 + i, ty + j - 2, (i + j) % 3 == 0 ? 0xFF3E7A2C : 0xFF4E9236);
                }
                fill(im, 0, 54, 64, 10, 0xFF4E8A36);
            }
            case "route" -> {
                vgrad(im, 0, 0, 64, 40, 0xFF3D3463, 0xFFE59A5C, 2);
                fill(im, 44, 10, 6, 6, 0xFFFFE6A0);
                for (int x = 0; x < 64; x++) {
                    int hh = 32 + (int) (3 * Math.sin(x * 0.18));
                    for (int y = hh; y < 64; y++) px(im, x, y, 0xFF3B4A33);
                }
                for (int y = 40; y < 64; y++) {
                    int half = 2 + (y - 40) / 2;
                    for (int x = 32 - half; x <= 32 + half; x++) px(im, x, y, (x + y) % 5 == 0 ? 0xFF9C8460 : 0xFFB89C70);
                }
                for (int t = 0; t < 3; t++) {
                    int tx = t * 26 + 2;
                    for (int j = 0; j < 22; j++) for (int i = -3; i <= 3; i++)
                        if (Math.abs(i) <= j / 3) px(im, tx + 5 + i, 16 + j, 0xFF2A3A28);
                }
            }
            case "port" -> {
                vgrad(im, 0, 0, 64, 34, 0xFF7EC0EA, 0xFFE0F0F6, 3);
                fill(im, 10, 12, 8, 2, 0xFFF4FAFC);
                for (int y = 34; y < 64; y++) for (int x = 0; x < 64; x++) {
                    int c = (y - 34) % 6 == 0 && (x + y) % 9 < 4 ? 0xFF9ED2EE : 0xFF2E6EA8;
                    if (y > 48) c = (x + y) % 7 == 0 ? 0xFF5E9ED0 : 0xFF245C90;
                    px(im, x, y, c);
                }
                // voilier
                fill(im, 42, 26, 14, 3, W2);
                for (int j = 0; j < 14; j++) for (int i = 0; i <= j / 2; i++) px(im, 48 - i, 12 + j, 0xFFF4EEDC);
                fill(im, 48, 10, 1, 17, W1);
                // ponton
                fill(im, 0, 44, 22, 3, W3); fill(im, 0, 47, 22, 1, W1);
                for (int k = 0; k < 3; k++) fill(im, 3 + k * 8, 47, 2, 17, W1);
            }
            case "atelier", "forge" -> {
                wood(im, 0, 0, 64, 64, 21, false);
                // étagère et outils
                fill(im, 0, 20, 64, 3, W1); fill(im, 0, 23, 64, 1, OUT);
                for (int k = 0; k < 4; k++) {
                    int x = 6 + k * 15;
                    fill(im, x, 8, 2, 12, W3);
                    fill(im, x - 2, 7, 6, 3, S3);
                }
                if (kind.equals("forge")) {
                    vgrad(im, 0, 34, 64, 30, 0xFF8A3A12, 0xFFF0A040, 4);
                }
                // lanterne chaude
                fill(im, 54, 28, 6, 8, G2); fill(im, 55, 29, 4, 6, 0xFFFFF0A8);
            }
            case "taverne" -> {
                wood(im, 0, 0, 64, 64, 33, true);
                vgrad(im, 0, 0, 64, 20, 0xFF5A3418, 0xFF7A4A22, 5);
                // tonneaux
                for (int k = 0; k < 3; k++) {
                    int bx = 2 + k * 22;
                    fill(im, bx, 40, 18, 24, W3);
                    fill(im, bx, 44, 18, 2, S2); fill(im, bx, 56, 18, 2, S2);
                    rect(im, bx, 40, 18, 24, OUT);
                }
                for (int i = 0; i < 64; i++) px(im, i, 38, OUT);
                fill(im, 8, 6, 6, 8, G2); fill(im, 9, 7, 4, 6, 0xFFFFF0A8);
            }
            case "mine" -> {
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    double n = smooth(x, y, 6, 8);
                    int c = n < 0.35 ? S0 : n < 0.7 ? S1 : S2;
                    if (rnd(x, y, 3) > 0.985) c = 0xFF8FD8E8; // éclats de minerai
                    px(im, x, y, c);
                }
                fill(im, 0, 0, 64, 5, W2); fill(im, 0, 5, 64, 1, OUT);
                fill(im, 4, 0, 5, 64, W2); fill(im, 55, 0, 5, 64, W2);
                fill(im, 28, 10, 6, 8, G2); fill(im, 29, 11, 4, 6, 0xFFFFF0A8);
            }
            case "chapelle" -> {
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    int c = ((y / 8) % 2 == 0 ? (x % 16) : ((x + 8) % 16)) == 0 || y % 8 == 0 ? S1 : S3;
                    if (rnd(x, y, 6) < 0.08) c = S2;
                    px(im, x, y, c);
                }
                // vitrail en arc
                for (int y = 6; y < 44; y++) for (int x = 20; x < 44; x++) {
                    double dx = x - 31.5, dy = y - 18;
                    boolean in = y >= 18 ? true : dx * dx + dy * dy <= 144;
                    if (!in) continue;
                    int[] glass = {0xFFC03838, 0xFF3868C0, 0xFFE0B83A, 0xFF3E9A4E};
                    int c = glass[((x - 20) / 6 + (y - 6) / 7) & 3];
                    if ((x - 20) % 6 == 0 || (y - 6) % 7 == 0) c = OUT;
                    px(im, x, y, c);
                }
            }
            case "mairie" -> {
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    int c = (y % 9 == 0 || ((y / 9) % 2 == 0 ? x % 14 == 0 : (x + 7) % 14 == 0)) ? 0xFF6E6250 : 0xFFA8977A;
                    px(im, x, y, c);
                }
                // bannières rouges et or
                for (int k = 0; k < 2; k++) {
                    int bx = 4 + k * 46;
                    fill(im, bx, 4, 12, 40, 0xFFB43030);
                    fill(im, bx, 4, 12, 2, G2);
                    for (int j = 0; j < 4; j++) { px(im, bx + j, 44 + j, 0xFFB43030); px(im, bx + 11 - j, 44 + j, 0xFFB43030); }
                    fill(im, bx + 4, 18, 4, 4, G3);
                }
            }
            case "dragon" -> {
                // Crépuscule : ciel nocturne en haut, lueur orangée à l'horizon, étoiles
                vgrad(im, 0, 0, 64, 44, 0xFF1C2246, 0xFFE8875A, 7);
                for (int y = 1; y < 16; y++) for (int x = 1; x < 63; x++)
                    if (rnd(x, y, 17) > 0.975) px(im, x, y, y < 8 ? 0xFFF4F4FF : 0xFFC8C8E8);
                // Croissant de lune (à gauche, hors du buste)
                for (int y = 3; y <= 11; y++) for (int x = 3; x <= 11; x++) {
                    double d1 = Math.hypot(x - 7, y - 7), d2 = Math.hypot(x - 9, y - 6);
                    if (d1 <= 3.6 && d2 > 3.2) px(im, x, y, 0xFFF4E8B0);
                }
                // Pics lointains enneigés, puis pics proches
                int[][] far = {{8, 26}, {24, 19}, {40, 24}, {56, 18}};
                int[][] near = {{0, 40}, {18, 34}, {36, 41}, {50, 33}, {64, 38}};
                for (int x = 0; x < 64; x++) {
                    int hf = 99, hn = 99;
                    for (int[] pk : far) hf = Math.min(hf, pk[1] + Math.abs(x - pk[0]));
                    for (int[] pk : near) hn = Math.min(hn, pk[1] + (int) Math.round(1.3 * Math.abs(x - pk[0])));
                    for (int y = hf; y < 64; y++) px(im, x, y, y < hf + 4 ? 0xFFE8EEF6 : 0xFF5A6286);
                    for (int y = hn; y < 64; y++) px(im, x, y, y < hn + 3 ? 0xFFD4DCE8 : 0xFF2E3450);
                }
                // Mer de Beurk et écume
                for (int y = 52; y < 64; y++) for (int x = 0; x < 64; x++) {
                    int c = (y - 52) % 4 == 0 && (x + 3 * y) % 11 < 3 ? 0xFF6FA8BC : 0xFF1F4A5E;
                    px(im, x, y, c);
                }
                // Aiguilles rocheuses sur les bords
                fill(im, 0, 38, 5, 26, 0xFF2A2E3C); fill(im, 4, 38, 1, 26, 0xFF464C60);
                fill(im, 59, 32, 5, 32, 0xFF2A2E3C); fill(im, 59, 32, 1, 32, 0xFF464C60);
                // Krokmou, Furie Nocturne, plane en haut à droite (œil vert)
                String[] fury = {
                        "...#..........#.....",
                        "...##........##.....",
                        "....###.....###.....",
                        ".....###...###......",
                        "##....#######.....##",
                        ".###############.###",
                        "....###########g###.",
                        ".......#...#........"};
                for (int j = 0; j < fury.length; j++) for (int i = 0; i < fury[j].length(); i++) {
                    char c = fury[j].charAt(i);
                    if (c == '#') px(im, 44 + i, 6 + j, 0xFF0E0E14);
                    else if (c == 'g') px(im, 44 + i, 6 + j, 0xFF7CFC6A);
                }
            }
            default -> throw new IllegalArgumentException(kind);
        }
        rect(im, 0, 0, 64, 64, OUT);
        return im;
    }

    /** Silhouette encapuchonnée (48 x 60) pour un personnage sans skin connu. */
    static BufferedImage silhouette() {
        BufferedImage im = img(48, 60);
        int cloak = 0xFF4A3424, cloakL = 0xFF6A4A32, face = 0xFF1A120C;
        for (int y = 0; y < 60; y++) for (int x = 0; x < 48; x++) {
            double cx = x - 23.5;
            boolean hood = y < 28 && cx * cx / 120.0 + (y - 16) * (y - 16) / 180.0 <= 1;
            boolean body = y >= 24 && Math.abs(cx) <= 10 + (y - 24) * 0.45;
            if (hood || body) px(im, x, y, (x + y) % 7 == 0 ? cloakL : cloak);
            boolean shadowFace = y >= 10 && y < 26 && cx * cx / 40.0 + (y - 18) * (y - 18) / 64.0 <= 1;
            if (shadowFace) px(im, x, y, face);
        }
        // yeux qui luisent
        px(im, 20, 18, G3); px(im, 27, 18, G3);
        return im;
    }

    /* =========================================================
       FOND COMMUN DE LA FENÊTRE (184 x 230, marge de 4 px)
       Coordonnées « g » = coordonnées dans le menu ; image = g + 4.
       ========================================================= */

    static final int M = 4;

    static int sx(int col) { return 7 + 18 * col; }   // gauche du cadre d'une case
    static int sy(int row) { return 17 + 18 * row; }  // haut du cadre d'une case (menu du haut)

    static BufferedImage windowBase(int seed) {
        BufferedImage im = img(176 + 2 * M, 222 + 2 * M);
        int W = im.getWidth(), H = im.getHeight();
        // Cadre en bois
        wood(im, 0, 0, W, H, seed, false);
        rect(im, 0, 0, W, H, OUT);
        rect(im, 5, 5, W - 10, H - 10, OUT);
        // Intérieur parchemin
        parchment(im, 6, 6, W - 12, H - 12, seed + 1);
        // Coins dorés
        goldCorner(im, 1, 1, 1, 1); goldCorner(im, W - 2, 1, -1, 1);
        goldCorner(im, 1, H - 2, 1, -1); goldCorner(im, W - 2, H - 2, -1, -1);
        // Bandeau du titre (planche sombre + plaque du nom)
        wood(im, 6, 6, W - 12, 15, seed + 2, true);
        for (int x = 6; x < W - 6; x++) { px(im, x, 21, G1); px(im, x, 22, OUT); }
        int nx = M + 26, nw = 124;
        recess(im, nx, M + 1, nw, 14, true);
        rect(im, nx + 1, M + 2, nw - 2, 12, G1);
        for (int i = nx + 2; i < nx + nw - 2; i++) px(im, i, M + 3, 0xFF2C1A0D);
        rivet(im, nx + 3, M + 7); rivet(im, nx + nw - 5, M + 7);
        // Sangle de fer entre les deux parties (le libellé « Inventaire » y devient invisible)
        int sy0 = M + 126;
        fill(im, 6, sy0, W - 12, 12, LABEL);
        for (int x = 6; x < W - 6; x++) { px(im, x, sy0, OUT); px(im, x, sy0 + 1, S3); px(im, x, sy0 + 10, S0); px(im, x, sy0 + 11, OUT); }
        for (int x = W - 40; x < W - 8; x += 12) rivet(im, x, sy0 + 5);
        // Inventaire du joueur : cases creusées
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slotWell(im, M + sx(c), M + 139 + 18 * r);
        for (int x = 6; x < W - 6; x++) { px(im, x, M + 194, P0); px(im, x, M + 195, W2); px(im, x, M + 196, P0); }
        for (int c = 0; c < 9; c++) slotWell(im, M + sx(c), M + 197);
        return im;
    }

    /** Logement d'un petit bouton (18 x 18) : anneau de fer. */
    static void socket(BufferedImage im, int gx, int gy) {
        int x = M + gx, y = M + gy;
        fill(im, x + 1, y + 1, 16, 16, S1);
        rect(im, x, y, 18, 18, OUT);
        for (int i = 1; i < 17; i++) { px(im, x + i, y + 1, S0); px(im, x + i, y + 16, S3); }
        clear(im, x, y); clear(im, x + 17, y); clear(im, x, y + 17); clear(im, x + 17, y + 17);
        px(im, x, y, P1); px(im, x + 17, y, P1); px(im, x, y + 17, P1); px(im, x + 17, y + 17, P1);
    }

    /** Écran « personnage » : portrait, 4 gros boutons, barre d'expérience. */
    static BufferedImage bgCharacter() {
        return bgCharacter(GOLD, "star");
    }

    /**
     * Écran d'un personnage. Variante « gestion » (logis, actions) : cadre du portrait et
     * médaillon turquoises, médaillon à la maison — un clic sur le portrait passe de l'une à l'autre.
     */
    static BufferedImage bgCharacter(int[] frame, String medal) {
        BufferedImage im = windowBase(11);
        // Rangée 0 : plaque d'état (colonnes 0-5) + 3 logements de boutons
        recess(im, M + sx(0), M + sy(0), 108, 18, true);
        rect(im, M + sx(0) + 1, M + sy(0) + 1, 106, 16, G1);
        for (int c = 6; c < 9; c++) socket(im, sx(c), sy(0));
        // Cadre du portrait (colonnes 0-3, rangées 1-4)
        int px0 = M + sx(0), py0 = M + sy(1);
        fill(im, px0, py0, 72, 72, OUT);
        bevel(im, px0, py0, 72, 72, frame);
        fill(im, px0 + 3, py0 + 3, 66, 66, OUT);
        fill(im, px0 + 4, py0 + 4, 64, 64, W0);
        goldCorner(im, px0 + 1, py0 + 1, 1, 1); goldCorner(im, px0 + 70, py0 + 1, -1, 1);
        goldCorner(im, px0 + 1, py0 + 70, 1, -1); goldCorner(im, px0 + 70, py0 + 70, -1, -1);
        // Logements des 4 boutons (colonnes 4-8)
        recess(im, M + sx(4), M + sy(1), 90, 72, true);
        // Rangée 5 : barre d'expérience
        int by = M + sy(5);
        recess(im, M + sx(0), by, 162, 18, true);
        fill(im, M + 27, by + 3, 140, 12, OUT);
        fill(im, M + 28, by + 4, 138, 10, 0xFF241509);
        for (int x = M + 28; x < M + 166; x++) px(im, x, by + 4, 0xFF140C05);
        // médaillon de niveau (étoile) ou de l'atelier (maison)
        bevel(im, M + sx(0) + 1, by + 1, 16, 16, frame);
        drawIcon(im, medal, M + sx(0) + 3, by + 3);
        return im;
    }

    /** Écran « liste » : en-tête, grille 9 x 4, pied de page. */
    static BufferedImage bgGrid(boolean footerPlate) {
        BufferedImage im = windowBase(23);
        socket(im, sx(0), sy(0));
        recess(im, M + sx(1), M + sy(0), 108, 18, true);
        socket(im, sx(7), sy(0));
        socket(im, sx(8), sy(0));
        for (int r = 1; r <= 4; r++) for (int c = 0; c < 9; c++) cardWell(im, M + sx(c), M + sy(r), r * 31 + c);
        if (footerPlate) {
            socket(im, sx(0), sy(5));
            recess(im, M + sx(1), M + sy(5), 126, 18, true);
            rect(im, M + sx(1) + 1, M + sy(5) + 1, 124, 16, G1);
            socket(im, sx(8), sy(5));
        }
        return im;
    }

    /** Écran « quêtes » d'un PNJ : deux cartes de quête et le bouton des récits. */
    static BufferedImage bgQuests() {
        BufferedImage im = windowBase(37);
        socket(im, sx(0), sy(0));
        recess(im, M + sx(1), M + sy(0), 108, 18, true);
        socket(im, sx(7), sy(0));
        socket(im, sx(8), sy(0));
        for (int card = 0; card < 2; card++) {
            int cx = M + sx(card == 0 ? 0 : 5), cy = M + sy(1);
            fill(im, cx, cy, 72, 72, OUT);
            parchment(im, cx + 1, cy + 1, 70, 70, 41 + card);
            rect(im, cx + 1, cy + 1, 70, 70, P0);
            // en-tête de carte
            fill(im, cx + 1, cy + 1, 70, 17, card == 0 ? BLUE[1] : PURPLE[1]);
            for (int x = cx + 1; x < cx + 71; x++) { px(im, x, cy + 1, card == 0 ? BLUE[3] : PURPLE[3]); px(im, x, cy + 17, OUT); }
            // emblème du type de quête (livre = principale, étoile = spéciale)
            drawIcon(im, card == 0 ? "book" : "star", cx + 3, cy + 3);
            // emplacements des objectifs
            for (int r = 2; r <= 3; r++) {
                slotWell(im, cx, M + sy(r));
                fill(im, cx + 21, M + sy(r) + 11, 49, 5, OUT);
                fill(im, cx + 22, M + sy(r) + 12, 47, 3, 0xFF5A4630);
            }
            // logement du bouton d'action
            recess(im, cx, M + sy(4), 72, 18, true);
            goldCorner(im, cx + 1, cy + 19, 1, 1); goldCorner(im, cx + 70, cy + 19, -1, 1);
        }
        recess(im, M + sx(4), M + sy(1), 18, 72, true);
        // Pied de page : rappel des règles (texte fixe)
        recess(im, M + sx(0), M + sy(5), 162, 18, true);
        textCentered(im, "ACHATS PNJ : NON COMPTÉS", M + 88, M + sy(5) + 6, P2, OUT);
        return im;
    }

    /** Écran « récits » (quêtes personnelles) : 5 lignes. */
    static BufferedImage bgPersonal() {
        BufferedImage im = windowBase(43);
        socket(im, sx(0), sy(0));
        recess(im, M + sx(1), M + sy(0), 108, 18, true);
        socket(im, sx(7), sy(0));
        socket(im, sx(8), sy(0));
        for (int r = 1; r <= 5; r++) {
            int y = M + sy(r);
            slotWell(im, M + sx(0), y);
            fill(im, M + sx(1), y, 126, 18, OUT);
            parchment(im, M + sx(1) + 1, y + 1, 124, 16, 50 + r);
            fill(im, M + sx(1) + 4, y + 12, 100, 4, OUT);
            fill(im, M + sx(1) + 5, y + 13, 98, 2, 0xFF5A4630);
            socket(im, sx(8), sy(r));
        }
        return im;
    }

    /** Écran des quêtes de la cité : grille 9 x 4 + récit + charte. */
    static BufferedImage bgCityQuests() {
        BufferedImage im = bgGrid(false);
        recess(im, M + sx(0), M + sy(5), 54, 18, true);
        recess(im, M + sx(3), M + sy(5), 108, 18, true);
        return im;
    }

    /** Écran des bâtiments : grille 9 x 4 + deux gros boutons. */
    static BufferedImage bgBuildings() {
        BufferedImage im = bgGrid(false);
        recess(im, M + sx(0), M + sy(5), 72, 18, true);
        socket(im, sx(4), sy(5));
        recess(im, M + sx(5), M + sy(5), 72, 18, true);
        return im;
    }

    /* =========================================================
       APERÇUS (vérification visuelle, x3)
       ========================================================= */

    static BufferedImage scale(BufferedImage src, int k) {
        BufferedImage im = img(src.getWidth() * k, src.getHeight() * k);
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++) im.setRGB(x, y, src.getRGB(x / k, y / k));
        return im;
    }

    /** Aperçu d'écran : fond « gris Minecraft » autour, fenêtre, éléments posés aux positions du jeu. */
    static class Preview {
        final BufferedImage im = img(176 + 40, 222 + 40);
        Preview(BufferedImage bg) {
            fill(im, 0, 0, im.getWidth(), im.getHeight(), 0xFF2B2B2B);
            blit(im, bg, 20 - M, 20 - M);
        }
        Preview put(BufferedImage sprite, int gx, int gy) { blit(im, sprite, 20 + gx, 20 + gy); return this; }
        Preview item(int col, int row, int color) {   // faux objet 16 x 16 dans une case du haut
            fill(im, 20 + 8 + 18 * col + 2, 20 + 18 + 18 * row + 2, 12, 12, color);
            rect(im, 20 + 8 + 18 * col + 2, 20 + 18 + 18 * row + 2, 12, 12, OUT);
            return this;
        }
        Preview text(String s, int gx, int gy, int color, boolean shadow) {
            ValmontArt.text(im, s, 20 + gx, 20 + gy + 2, color, shadow ? lerp(OUT, color, 0.25) : T);
            return this;
        }
        Preview textC(String s, int cx, int gy, int color, boolean shadow) {
            return text(s, cx - textWidth(s, shadow) / 2, gy, color, shadow);
        }
        void save(File f) throws IOException { ImageIO.write(scale(im, 3), "png", f); }
    }

    /* =========================================================
       GÉNÉRATION
       ========================================================= */

    static File uiDir;

    static void save(BufferedImage im, String name) throws IOException {
        // Une image de police de plus de 256 px s'affiche comme un caractère manquant
        if (im.getWidth() > 256 || im.getHeight() > 256) {
            throw new IllegalStateException(name + " : " + im.getWidth() + "x" + im.getHeight() + " dépasse 256 px");
        }
        ImageIO.write(im, "png", new File(uiDir, name + ".png"));
    }

    public static void main(String[] args) throws IOException {
        File root = new File(args.length > 0 ? args[0] : ".");
        uiDir = new File(root, "src/main/resources/pack/assets/citycore/textures/ui");
        File meta = new File(root, "src/main/resources/ui");
        File previews = new File(root, "docs/ui-previews");
        uiDir.mkdirs(); meta.mkdirs(); previews.mkdirs();
        for (File f : uiDir.listFiles()) if (f.getName().endsWith(".png")) f.delete();

        // ── Police ──────────────────────────────────────────
        List<Character> chars = new ArrayList<>(GLYPHS.keySet());
        save(fontSheet(chars, false), "font");
        save(fontSheet(chars, true), "font_shadow");
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < chars.size(); k++) {
            sb.append(chars.get(k));
            if (k % 16 == 15) sb.append('\n');
        }
        Files.writeString(new File(meta, "font_chars.txt").toPath(), sb.toString(), StandardCharsets.UTF_8);

        // ── Fonds ───────────────────────────────────────────
        BufferedImage bgChar = bgCharacter(), bgList = bgGrid(true), bgQ = bgQuests(), bgP = bgPersonal();
        BufferedImage bgCQ = bgCityQuests(), bgB = bgBuildings();
        save(bgChar, "bg_character"); save(bgList, "bg_grid"); save(bgQ, "bg_quests");
        save(bgP, "bg_personal");     save(bgCQ, "bg_cityquests"); save(bgB, "bg_buildings");
        save(bgCharacter(TEAL, "house"), "bg_character_gestion"); // écran « gestion » d'un artisan

        // ── Gros boutons 90 x 18 ────────────────────────────
        Map<String, BufferedImage> big = new LinkedHashMap<>();
        big.put("btn_vendre",     button(90, 18, GREEN,  "coins",  "VENDRE"));
        big.put("btn_entrepot",   button(90, 18, AMBER,  "crate",  "ENTREPÔT"));
        big.put("btn_boutique",   button(90, 18, BLUE,   "stall",  "BOUTIQUE"));
        big.put("btn_suivre",     button(90, 18, GREEN,  "boots",  "SUIVEZ-MOI"));
        big.put("btn_arreter",    button(90, 18, RED,    "stop",   "ATTENDEZ ICI"));
        big.put("btn_logis",      button(90, 18, TEAL,   "house",  "SON LOGIS"));
        big.put("btn_promener",   button(90, 18, GREEN,  "walk",   "PROMENADE"));
        big.put("btn_rester",     button(90, 18, BROWN,  "flag",   "RESTEZ ICI"));
        big.put("btn_quetes",     button(90, 18, PURPLE, "scroll", "QUÊTES"));
        big.put("btn_batiments",  button(90, 18, TEAL,   "house",  "BÂTIMENTS"));
        big.put("btn_tresor",     button(90, 18, GOLD,   "chest",  "TRÉSOR"));
        big.put("btn_agrandir",   button(90, 18, GREEN,  "map",    "AGRANDIR"));
        big.put("btn_agrandir_off", button(90, 18, GRAY, "map",    "AGRANDIR"));
        big.put("btn_deposer",    button(90, 18, GREEN,  "coinup", "DÉPOSER"));
        big.put("btn_histoire",   button(90, 18, BROWN,  "book",   "SON HISTOIRE"));
        big.put("btn_actions",    button(90, 18, RED,    "shield", "ACTIONS"));
        big.put("plate",          plate(90));
        for (var e : big.entrySet()) save(e.getValue(), e.getKey());

        // ── Boutons d'action 72 x 18 et 54 / 108 x 18 ──────
        Map<String, BufferedImage> mid = new LinkedHashMap<>();
        mid.put("qb_accepter",  button(72, 18, GREEN,  "check",     "ACCEPTER"));
        mid.put("qb_valider",   button(72, 18, GOLD,   "star",      "VALIDER"));
        mid.put("qb_encours",   button(72, 18, GRAY,   "hourglass", "EN COURS"));
        mid.put("qb_repos",     button(72, 18, GRAY,   "clock",     "EN REPOS"));
        mid.put("qb_aucune",    button(72, 18, GRAY,   "cross",     "AUCUNE"));
        mid.put("fb_ajouter",   button(72, 18, GREEN,  "house",     "AJOUTER"));
        mid.put("fb_supprimer", button(72, 18, RED,    "cross",     "RETIRER"));
        mid.put("fb_retour",    button(72, 18, BROWN,  "left",      "RETOUR"));
        mid.put("fb_recit",     button(54, 18, BROWN,  "book",      "RÉCIT"));
        mid.put("fb_charte",       button(108, 18, GREEN, "quill", "SIGNER CHARTE"));
        mid.put("fb_charte_fonds", button(108, 18, GOLD,  "chest", "CAISSE VIDE"));
        mid.put("fb_charte_lock",  button(108, 18, GRAY,  "lock",  "QUÊTES À FINIR"));
        mid.put("fb_capitale",     button(108, 18, GOLD,  "crown", "CAPITALE"));
        mid.put("vb_recits",     verticalButton(PURPLE, "quill", "RÉCITS", false));
        mid.put("vb_recits_off", verticalButton(GRAY,   "quill", "RÉCITS", true));
        for (var e : mid.entrySet()) save(e.getValue(), e.getKey());

        // ── Petits boutons 18 x 18 ──────────────────────────
        Map<String, BufferedImage> small = new LinkedHashMap<>();
        small.put("ic_close",      iconButton(RED,    "cross",  false));
        small.put("ic_back",       iconButton(BROWN,  "left",   false));
        small.put("ic_prev",       iconButton(BROWN,  "left",   false));
        small.put("ic_next",       iconButton(BROWN,  "right",  false));
        small.put("ic_prev_off",   iconButton(GRAY,   "left",   true));
        small.put("ic_next_off",   iconButton(GRAY,   "right",  true));
        small.put("ic_info",       iconButton(BLUE,   "info",   false));
        small.put("ic_follow",     iconButton(GREEN,  "boots",  false));
        small.put("ic_follow_stop",iconButton(RED,    "stop",   false));
        small.put("ic_job",        iconButton(BLUE,   "hammer", false));
        small.put("ic_job_quit",   iconButton(RED,    "hammer", false));
        small.put("ic_job_locked", iconButton(GRAY,   "hammer", true));
        small.put("ic_story",      iconButton(BROWN,  "book",   false));
        small.put("ic_purse",      iconButton(GOLD,   "coins",  false));
        small.put("ic_map",        iconButton(TEAL,   "map",    false)); // vue d'ensemble (/city locate)
        for (var e : small.entrySet()) save(e.getValue(), e.getKey());

        // ── Bannières 108 x 18 ──────────────────────────────
        Map<String, BufferedImage> ribs = new LinkedHashMap<>();
        ribs.put("rib_vendre",    ribbon(GREEN,  "VENDRE"));
        ribs.put("rib_entrepot",  ribbon(AMBER,  "ENTREPÔT"));
        ribs.put("rib_boutique",  ribbon(BLUE,   "BOUTIQUE"));
        ribs.put("rib_quetes_pnj", ribbon(PURPLE, "QUÊTES"));
        ribs.put("rib_recits",    ribbon(PURPLE, "RÉCITS"));
        ribs.put("rib_logis",     ribbon(TEAL,   "LOGIS LIBRES"));
        ribs.put("rib_batiments", ribbon(TEAL,   "BÂTIMENTS"));
        ribs.put("rib_supprimer", ribbon(RED,    "SUPPRIMER"));
        ribs.put("rib_quetes",    ribbon(GOLD,   "QUÊTES"));
        ribs.put("rib_atelier",   ribbon(TEAL,   "SON ATELIER"));
        ribs.put("rib_actions",   ribbon(RED,    "ACTIONS"));
        ribs.put("rib_garde",     ribbon(RED,    "LA GARDE"));
        ribs.put("rib_role",      ribbon(RED,    "RÔLE"));
        ribs.put("rib_population", ribbon(GOLD,  "POPULATION"));
        ribs.put("rib_employes",  ribbon(BLUE,   "EMPLOYÉS"));
        ribs.put("rib_annexe",    ribbon(TEAL,   "SON ANNEXE"));
        for (var e : ribs.entrySet()) save(e.getValue(), e.getKey());

        // ── États des cases, pastilles, étoiles, barres ─────
        save(ring(GREEN), "ring_green");   save(ring(AMBER), "ring_amber");
        save(ring(RED), "ring_red");       save(ring(GRAY), "ring_gray");
        save(ring(PURPLE), "ring_purple"); save(ring(GOLD), "ring_gold");
        save(badge(GREEN, "check"), "badge_check");
        save(badge(AMBER, "dot"), "badge_progress");
        save(badge(RED, "cross"), "badge_empty");
        save(badge(GOLD, "bang"), "badge_new");
        BufferedImage lock = img(12, 12); drawIcon(lock, "lock", 0, 0); save(lock, "lock");
        BufferedImage coin = img(9, 9);
        fill(coin, 1, 0, 7, 9, G2); fill(coin, 0, 1, 9, 7, G2); fill(coin, 2, 1, 5, 7, G3);
        rect(coin, 1, 0, 7, 9, G0); px(coin, 0, 1, G0); px(coin, 8, 1, G0); px(coin, 0, 7, G0); px(coin, 8, 7, G0);
        fill(coin, 0, 2, 1, 5, G0); fill(coin, 8, 2, 1, 5, G0); px(coin, 3, 2, G4); px(coin, 3, 3, G4);
        save(coin, "coin");
        for (int n = 0; n <= 5; n++) save(stars(n), "stars_" + n);
        save(segment(10, new int[]{G4, G3, G3, G2, G2, G1}), "seg_xp");
        save(segment(10, new int[]{0xFFA5E07A, 0xFF6DBB45, 0xFF45922F, 0xFF2E6B22}), "seg_xp_max");
        save(segment(3, new int[]{0xFFA5E07A, 0xFF6DBB45, 0xFF2E6B22}), "seg_green");
        save(segment(3, new int[]{G4, G2, G1}), "seg_gold");
        save(segment(2, new int[]{0xFFA5E07A, 0xFF45922F}), "seg_thin");
        BufferedImage px3 = img(3, 3); fill(px3, 0, 0, 3, 3, 0xFFFFFFFF); save(px3, "px");
        BufferedImage px2 = img(2, 2); fill(px2, 0, 0, 2, 2, 0xFFFFFFFF); save(px2, "px2"); // carte (/city locate)

        // ── Portraits ───────────────────────────────────────
        for (String k : List.of("nature", "route", "port", "atelier", "forge", "taverne", "mine", "chapelle", "mairie", "dragon")) {
            save(backdrop(k), "pf_" + k);
        }
        save(silhouette(), "silhouette");

        // ── HUD compact : panneau des quêtes et panneaux du Tab ─
        List<Character> smallChars = new ArrayList<>(SMALL.keySet());
        save(smallSheet(smallChars), "font_small");
        StringBuilder sc = new StringBuilder();
        for (int k = 0; k < smallChars.size(); k++) {
            sc.append(smallChars.get(k));
            if (k % 16 == 15) sc.append('\n');
        }
        Files.writeString(new File(meta, "font_small_chars.txt").toPath(), sc.toString(), StandardCharsets.UTF_8);
        save(hudTop(), "hud_top");
        save(hudHead(), "hud_head");
        save(hudRow(true), "hud_row");
        save(hudRow(false), "hud_plain");
        save(hudBottom(), "hud_bottom");
        save(segment(1, new int[]{0xFF7CC45A}), "seg_hud");
        save(segment(1, new int[]{G3}), "seg_hud_gold");

        // ── Menu « Quêtes suivies » ─────────────────────────
        save(bgTracking(), "bg_tracking");
        // Onglets du journal de quêtes (doré = onglet ouvert) : SUIVI (colonnes 0-2), RÉCITS (3-6)
        save(button(54, 18, GOLD,  "scroll", "SUIVI"),  "tab_suivi_on");
        save(button(54, 18, BROWN, "scroll", "SUIVI"),  "tab_suivi_off");
        save(button(72, 18, GOLD,  "quill",  "RÉCITS"), "tab_recits_on");
        save(button(72, 18, BROWN, "quill",  "RÉCITS"), "tab_recits_off");
        save(bgJournal(), "bg_journal");
        save(button(54, 18, BROWN, "pos_edge", "BORD"), "pos_bord");
        save(button(54, 18, BROWN, "pos_margin", "MARGE"), "pos_gauche");
        save(button(54, 18, BROWN, "pos_near", "PRÈS"), "pos_pres");
        save(selected54(), "sel54");
        // Affichage du suivi des quêtes : visible, accroché à la liste des joueurs (Tab), masqué
        save(button(108, 18, GREEN, "check", "VISIBLE"), "hud_on");
        save(button(108, 18, TEAL, "pos_tab", "ACCROCHÉ"), "hud_tab");
        save(button(108, 18, GRAY, "cross", "MASQUÉ"), "hud_off");
        save(button(54, 18, BLUE, "check", "TOUS"), "track_all");

        // ── Où sont-ils ? (/city locate) ────────────────────
        BufferedImage bgLoc = bgLocate();
        save(bgLoc, "bg_locate");
        previewLocate(previews, bgLoc);

        // ── Aperçus ─────────────────────────────────────────
        writePreviews(previews, bgChar, bgList, bgQ, bgP, bgCQ, bgB);
        System.out.println("OK : " + uiDir.listFiles().length + " images, " + chars.size() + " caractères.");
    }

    /* =========================================================
       LISTE DES JOUEURS (TOUCHE TAB)
       ========================================================= */

    /** Icônes 9 x 9 de la bannière Tab. */
    static final Map<String, String[]> MINI = new LinkedHashMap<>();
    static {
        MINI.put("star", new String[]{
                "....k....", "...kyk...", "kkkkYkkkk", "kYYYYYYYk", ".kOYYYOk.",
                "..kYYYk..", ".kYOkOYk.", ".kOk.kOk.", ".kk...kk."});
        MINI.put("coin", new String[]{
                "..kkkkk..", ".kYYYYYk.", "kYyYYYOYk", "kYyYOYOYk", "kYYYOYOYk",
                "kYYYYYOYk", "kOYYYYOOk", ".kOOOOOk.", "..kkkkk.."});
        MINI.put("hammer", new String[]{
                "kkkkkk...", "ksssssk..", "kSSSSSk..", "kkkknk...", "...knk...",
                "...knk...", "...knk...", "...kmk...", "...kkk..."});
        MINI.put("crown", new String[]{
                ".........", "k...k...k", "kk.kYk.kk", "kYkYYYkYk", "kYYYrYYYk",
                "kYYrrrYYk", "kOOOOOOOk", "kkkkkkkkk", "........."});
        MINI.put("people", new String[]{
                "...kkk...", "..kfffk..", "..kfffk..", "...kkk...", "..kBBBk..",
                ".kBBBBBk.", ".kBkBkBk.", "..kkkkk..", "........."});
        MINI.put("chest", new String[]{
                ".kkkkkkk.", "kNNNNNNNk", "kmmmmmmmk", "kkkkYkkkk", "kNNkYkNNk",
                "kNNNNNNNk", "kmmmmmmmk", "kkkkkkkkk", "........."});
        MINI.put("map", new String[]{
                "kkkkkkkkk", "kpPpPpPpk", "kpgPpPgpk", "kpggPggpk", "kpPrPpPpk",
                "kpPrrPpPk", "kpPpPrPpk", "kpPpPpPpk", "kkkkkkkkk"});
        MINI.put("sword", new String[]{
                ".......kk", "......ksk", ".....kskk", "kk..ksk..", ".kkksk...",
                "..kkk....", ".kNkk....", "kNk.kk...", "kk......."});
    }

    static void drawMini(BufferedImage im, String name, int x, int y) {
        String[] rows = MINI.get(name);
        for (int j = 0; j < rows.length; j++)
            for (int i = 0; i < rows[j].length(); i++) {
                char c = rows[j].charAt(i);
                if (c != '.') px(im, x + i, y + j, PAL.get(c));
            }
    }

    // Géométrie de la bannière Tab (reprise par UiLayout côté plugin)
    static final int TAB_W = 276, TAB_H = 70;

    /** Panneau d'une section : creux sombre, bandeau titre coloré, icônes des lignes. */
    static void tabPanel(BufferedImage im, int x, int w, int[] scheme, String title, String titleIcon, String... icons) {
        recess(im, x, 20, w, 46, true);
        rect(im, x + 1, 21, w - 2, 44, G1);
        fill(im, x + 2, 22, w - 4, 9, scheme[2]);
        for (int i = x + 2; i < x + w - 2; i++) { px(im, i, 22, scheme[4]); px(im, i, 30, scheme[0]); }
        drawMini(im, titleIcon, x + 4, 22);
        text(im, title, x + 16, 23, P4, scheme[0]);
        for (int r = 0; r < icons.length; r++) {
            if (icons[r] != null) drawMini(im, icons[r], x + 4, 33 + 11 * r);
        }
    }

    /** Bannière de la liste des joueurs : titre, panneau Joueur, panneau Ville. */
    static BufferedImage tabBanner() {
        BufferedImage im = img(TAB_W, TAB_H);
        wood(im, 0, 0, TAB_W, TAB_H, 71, false);
        rect(im, 0, 0, TAB_W, TAB_H, OUT);
        rect(im, 3, 3, TAB_W - 6, TAB_H - 6, OUT);
        parchment(im, 4, 4, TAB_W - 8, TAB_H - 8, 72);
        goldCorner(im, 1, 1, 1, 1); goldCorner(im, TAB_W - 2, 1, -1, 1);
        goldCorner(im, 1, TAB_H - 2, 1, -1); goldCorner(im, TAB_W - 2, TAB_H - 2, -1, -1);
        // Ruban doré du titre (le texte est ajouté par le plugin)
        int rw = 150, rx = (TAB_W - rw) / 2;
        BufferedImage rib = img(rw, 16);
        bevel(rib, 0, 0, rw, 16, GOLD);
        for (int i = 2; i < rw - 2; i++) px(rib, i, 3, GOLD[1]);
        blit(im, rib, rx, 2);
        rivet(im, rx + 4, 8); rivet(im, rx + rw - 6, 8);
        // Panneaux
        tabPanel(im, 6, 130, BLUE, "JOUEUR", "sword", "star", "coin", "hammer");
        tabPanel(im, 140, 130, GREEN, "VILLE", "crown", "crown", "people", "map");
        drawMini(im, "chest", 140 + 66, 44);                 // trésor, à côté des habitants
        // Rail de la barre d'expérience
        fill(im, 62, 34, 70, 7, OUT);
        fill(im, 63, 35, 68, 5, 0xFF241509);
        return im;
    }

    /* =========================================================
       PANNEAU « QUÊTES ACTIVES » (HUD, à gauche de l'écran)
       Assemblé ligne par ligne par le plugin : haut, lignes de 10 px, bas.
       ========================================================= */

    static final int QP_W = 116;

    /** Côtés en bois d'une pièce du panneau (contour, bois, liseré doré). */
    static void qpSides(BufferedImage im, int h) {
        for (int y = 0; y < h; y++) {
            px(im, 0, y, OUT); px(im, QP_W - 1, y, OUT);
            px(im, 1, y, W3);  px(im, QP_W - 2, y, W1);
            px(im, 2, y, W2);  px(im, QP_W - 3, y, W2);
            px(im, 3, y, G1);  px(im, QP_W - 4, y, G1);
        }
    }

    /** Haut : bordure, bandeau doré « QUÊTES ACTIVES ». */
    static BufferedImage qpTop() {
        BufferedImage im = img(QP_W, 18);
        wood(im, 0, 0, QP_W, 18, 81, false);
        rect(im, 0, 0, QP_W, 18, OUT);
        clear(im, 0, 17); clear(im, QP_W - 1, 17);
        for (int y = 16; y < 18; y++) { px(im, 0, y, OUT); px(im, QP_W - 1, y, OUT); }
        bevel(im, 3, 2, QP_W - 6, 13, GOLD);
        textCentered(im, "QUÊTES ACTIVES", QP_W / 2, 5, P4, GOLD[0]);
        fill(im, 4, 16, QP_W - 8, 2, W0);
        for (int x = 4; x < QP_W - 4; x++) px(im, x, 16, OUT);
        px(im, 1, 1, G4); px(im, QP_W - 2, 1, G4);
        return im;
    }

    /** Ligne de titre d'un habitant (bandeau un peu plus clair). */
    static BufferedImage qpHead() {
        BufferedImage im = img(QP_W, 10);
        fill(im, 4, 0, QP_W - 8, 10, W1);
        for (int x = 4; x < QP_W - 4; x++) { px(im, x, 0, W2); px(im, x, 9, 0xFF2C1A0D); }
        qpSides(im, 10);
        return im;
    }

    /** Ligne d'objectif avec rail de progression (2 px en bas). */
    static BufferedImage qpRow(boolean track) {
        BufferedImage im = img(QP_W, 10);
        fill(im, 4, 0, QP_W - 8, 10, W0);
        if (track) {
            for (int x = 6; x < QP_W - 6; x++) { px(im, x, 8, OUT); px(im, x, 9, 0xFF140C05); }
        }
        qpSides(im, 10);
        return im;
    }

    /** Bas : bordure et coins dorés. */
    static BufferedImage qpBottom() {
        BufferedImage im = img(QP_W, 6);
        wood(im, 0, 0, QP_W, 6, 83, false);
        for (int x = 4; x < QP_W - 4; x++) px(im, x, 0, G1);
        rect(im, 0, -1, QP_W, 7, OUT);
        px(im, 1, 4, G4); px(im, QP_W - 2, 4, G4); px(im, 2, 4, G2); px(im, QP_W - 3, 4, G2);
        return im;
    }

    /* =========================================================
       PETITE POLICE (capitales 3 x 5) — HUD compact
       Cellule de 6 x 8 : accents lignes 0-1, capitales 2-6, jambage ligne 7.
       ========================================================= */

    static final Map<Character, String[]> SMALL = new LinkedHashMap<>();
    static final Map<Character, String[]> SMALL_ACC = new HashMap<>();
    static final Map<Character, String> SMALL_DESC = new HashMap<>();

    static void sg(char c, String... rows) { SMALL.put(c, rows); }

    static {
        sg('A', ".#.", "#.#", "###", "#.#", "#.#");
        sg('B', "##.", "#.#", "##.", "#.#", "##.");
        sg('C', ".##", "#..", "#..", "#..", ".##");
        sg('D', "##.", "#.#", "#.#", "#.#", "##.");
        sg('E', "###", "#..", "##.", "#..", "###");
        sg('F', "###", "#..", "##.", "#..", "#..");
        sg('G', ".##", "#..", "#.#", "#.#", ".##");
        sg('H', "#.#", "#.#", "###", "#.#", "#.#");
        sg('I', "###", ".#.", ".#.", ".#.", "###");
        sg('J', "..#", "..#", "..#", "#.#", ".#.");
        sg('K', "#.#", "#.#", "##.", "#.#", "#.#");
        sg('L', "#..", "#..", "#..", "#..", "###");
        sg('M', "#...#", "##.##", "#.#.#", "#...#", "#...#");
        sg('N', "#..#", "##.#", "#.##", "#..#", "#..#");
        sg('O', ".#.", "#.#", "#.#", "#.#", ".#.");
        sg('P', "##.", "#.#", "##.", "#..", "#..");
        sg('Q', ".#.", "#.#", "#.#", "##.", ".##");
        sg('R', "##.", "#.#", "##.", "#.#", "#.#");
        sg('S', ".##", "#..", ".#.", "..#", "##.");
        sg('T', "###", ".#.", ".#.", ".#.", ".#.");
        sg('U', "#.#", "#.#", "#.#", "#.#", "###");
        sg('V', "#.#", "#.#", "#.#", "#.#", ".#.");
        sg('W', "#...#", "#...#", "#.#.#", "##.##", "#...#");
        sg('X', "#.#", "#.#", ".#.", "#.#", "#.#");
        sg('Y', "#.#", "#.#", ".#.", ".#.", ".#.");
        sg('Z', "###", "..#", ".#.", "#..", "###");
        sg('0', "###", "#.#", "#.#", "#.#", "###");
        sg('1', ".#.", "##.", ".#.", ".#.", "###");
        sg('2', "##.", "..#", ".#.", "#..", "###");
        sg('3', "##.", "..#", ".#.", "..#", "##.");
        sg('4', "#.#", "#.#", "###", "..#", "..#");
        sg('5', "###", "#..", "##.", "..#", "##.");
        sg('6', ".##", "#..", "###", "#.#", "###");
        sg('7', "###", "..#", ".#.", ".#.", ".#.");
        sg('8', "###", "#.#", "###", "#.#", "###");
        sg('9', "###", "#.#", "###", "..#", "##.");
        sg('.', ".", ".", ".", ".", "#");
        sg(',', ".", ".", ".", ".", "#");
        sg(':', ".", "#", ".", "#", ".");
        sg(';', ".", "#", ".", "#", ".");
        sg('!', "#", "#", "#", ".", "#");
        sg('?', "##.", "..#", ".#.", "...", ".#.");
        sg('\'', "#", "#", ".", ".", ".");
        sg('-', "...", "...", "###", "...", "...");
        sg('+', "...", ".#.", "###", ".#.", "...");
        sg('/', "..#", "..#", ".#.", "#..", "#..");
        sg('%', "#.#", "..#", ".#.", "#..", "#.#");
        sg('(', ".#", "#.", "#.", "#.", ".#");
        sg(')', "#.", ".#", ".#", ".#", "#.");
        sg('=', "...", "###", "...", "###", "...");
        sg('*', "#.#", ".#.", "#.#", "...", "...");
        sg('#', "#.#", "###", "#.#", "###", "#.#");
        sg('&', ".#.", "#.#", ".#.", "#.#", ".##");
        sg('_', "...", "...", "...", "...", "###");
        sg('\u00B7', ".", ".", "#", ".", ".");                                    // ·
        sg('\u2022', "..", "##", "##", "..", "..");                               // •
        sg('\u2192', "....", "..#.", "####", "..#.", "....");                     // →
        sg('\u2190', "....", ".#..", "####", ".#..", "....");                     // ←
        sg('\u2605', "..#..", "#####", ".###.", ".#.#.", "#...#");                // ★
        sg('\u2714', "...", "..#", "#.#", ".#.", "...");                          // ✔
        sg('\u00D7', "...", "#.#", ".#.", "#.#", "...");                          // ×
        sg('\u0152', ".####", "#.#..", "#.##.", "#.#..", ".####");                // Œ
        sg('\u00C6', ".####", "#.#..", "####.", "#.#..", "#.###");                // Æ
        smallAccent('\u00C0', 'A', "grave"); smallAccent('\u00C2', 'A', "circ");  smallAccent('\u00C4', 'A', "diaer");
        smallAccent('\u00C8', 'E', "grave"); smallAccent('\u00C9', 'E', "acute"); smallAccent('\u00CA', 'E', "circ");
        smallAccent('\u00CB', 'E', "diaer"); smallAccent('\u00CE', 'I', "circ");  smallAccent('\u00CF', 'I', "diaer");
        smallAccent('\u00D4', 'O', "circ");  smallAccent('\u00D6', 'O', "diaer"); smallAccent('\u00D9', 'U', "grave");
        smallAccent('\u00DB', 'U', "circ");  smallAccent('\u00DC', 'U', "diaer"); smallAccent('\u0178', 'Y', "diaer");
        smallAccent('\u00C7', 'C', "cedil");
        SMALL_DESC.put(',', "#"); SMALL_DESC.put(';', "#");
    }

    static void smallAccent(char c, char base, String kind) {
        String[] rows = SMALL.get(base);
        int w = rows[0].length(), mid = w / 2;
        char[] r0 = ".".repeat(w).toCharArray(), r1 = ".".repeat(w).toCharArray();
        switch (kind) {
            case "acute" -> { r0[Math.min(w - 1, mid + 1)] = '#'; r1[mid] = '#'; }
            case "grave" -> { r0[Math.max(0, mid - 1)] = '#'; r1[mid] = '#'; }
            case "circ"  -> { r0[mid] = '#'; r1[Math.max(0, mid - 1)] = '#'; r1[Math.min(w - 1, mid + 1)] = '#'; }
            case "diaer" -> { r1[0] = '#'; r1[w - 1] = '#'; }
            case "cedil" -> { }
            default -> throw new IllegalArgumentException(kind);
        }
        SMALL.put(c, rows);
        if (kind.equals("cedil")) {
            char[] d = ".".repeat(w).toCharArray();
            d[mid] = '#';
            SMALL_DESC.put(c, new String(d));
        } else {
            SMALL_ACC.put(c, new String[]{new String(r0), new String(r1)});
        }
    }

    /** Glyphe de la petite police : (x, y) = haut gauche de la capitale. */
    static void smallGlyph(BufferedImage im, char c, int x, int y, int color) {
        String[] rows = SMALL.get(c);
        if (rows == null) return;
        for (int j = 0; j < rows.length; j++)
            for (int i = 0; i < rows[j].length(); i++)
                if (rows[j].charAt(i) == '#') px(im, x + i, y + j, color);
        String[] acc = SMALL_ACC.get(c);
        if (acc != null) for (int j = 0; j < 2; j++)
            for (int i = 0; i < acc[j].length(); i++)
                if (acc[j].charAt(i) == '#') px(im, x + i, y - 2 + j, color);
        String desc = SMALL_DESC.get(c);
        if (desc != null) for (int i = 0; i < desc.length(); i++)
            if (desc.charAt(i) == '#') px(im, x + i, y + 5, color);
    }

    static int smallWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += (c == ' ' ? 3 : SMALL.containsKey(c) ? SMALL.get(c)[0].length() : 3) + 1;
        }
        return Math.max(0, w - 1);
    }

    /** Texte en petite police avec ombre (images fixes). y = haut de la capitale. */
    static void smallText(BufferedImage im, String s, int x, int y, int color, int shadow) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (shadow != T) smallGlyph(im, c, cx + 1, y + 1, shadow);
            smallGlyph(im, c, cx, y, color);
            cx += (c == ' ' ? 3 : SMALL.get(c)[0].length()) + 1;
        }
    }

    /** Planche de la petite police : 16 colonnes, cellules 6 x 8, glyphes blancs. */
    static BufferedImage smallSheet(List<Character> chars) {
        int rows = (chars.size() + 15) / 16;
        BufferedImage im = img(16 * 6, rows * 8);
        for (int k = 0; k < chars.size(); k++) {
            smallGlyph(im, chars.get(k), (k % 16) * 6, (k / 16) * 8 + 2, 0xFFFFFFFF);
        }
        return im;
    }

    /* =========================================================
       HUD COMPACT (panneau des quêtes, panneaux du Tab) — 84 px de large
       ========================================================= */

    static final int HUD_W = 84;

    static void hudSides(BufferedImage im, int h) {
        for (int y = 0; y < h; y++) {
            px(im, 0, y, OUT); px(im, HUD_W - 1, y, OUT);
            px(im, 1, y, W3);  px(im, HUD_W - 2, y, W1);
            px(im, 2, y, G1);  px(im, HUD_W - 3, y, G1);
        }
    }

    /** Haut : bordure et bandeau doré (le titre est écrit par le plugin). */
    static BufferedImage hudTop() {
        BufferedImage im = img(HUD_W, 12);
        wood(im, 0, 0, HUD_W, 12, 91, false);
        rect(im, 0, 0, HUD_W, 12, OUT);
        // Bandeau doré : ses coins arrondis laissent voir le bois, pas le décor du jeu
        int[] corners = {im.getRGB(2, 1), im.getRGB(HUD_W - 3, 1), im.getRGB(2, 10), im.getRGB(HUD_W - 3, 10)};
        bevel(im, 2, 1, HUD_W - 4, 10, GOLD);
        px(im, 2, 1, corners[0]); px(im, HUD_W - 3, 1, corners[1]);
        px(im, 2, 10, corners[2]); px(im, HUD_W - 3, 10, corners[3]);
        // Dernière rangée : filet sombre, raccordé aux côtés des lignes (bois, filet doré)
        fill(im, 3, 11, HUD_W - 6, 1, OUT);
        px(im, 1, 11, W3); px(im, 2, 11, G1); px(im, HUD_W - 3, 11, G1); px(im, HUD_W - 2, 11, W1);
        px(im, 1, 1, G4); px(im, HUD_W - 2, 1, G4);
        return im;
    }

    static BufferedImage hudHead() {
        BufferedImage im = img(HUD_W, 8);
        fill(im, 3, 0, HUD_W - 6, 8, W1);
        for (int x = 3; x < HUD_W - 3; x++) { px(im, x, 0, W2); px(im, x, 7, 0xFF2C1A0D); }
        hudSides(im, 8);
        return im;
    }

    static BufferedImage hudRow(boolean track) {
        BufferedImage im = img(HUD_W, 8);
        fill(im, 3, 0, HUD_W - 6, 8, W0);
        if (track) for (int x = 5; x < HUD_W - 5; x++) px(im, x, 7, 0xFF120A04);
        hudSides(im, 8);
        return im;
    }

    static BufferedImage hudBottom() {
        BufferedImage im = img(HUD_W, 3);
        fill(im, 0, 0, HUD_W, 3, W2);
        for (int x = 3; x < HUD_W - 3; x++) px(im, x, 0, G1);
        for (int x = 0; x < HUD_W; x++) px(im, x, 2, OUT);
        px(im, 0, 0, OUT); px(im, 0, 1, OUT); px(im, HUD_W - 1, 0, OUT); px(im, HUD_W - 1, 1, OUT);
        px(im, 1, 1, G4); px(im, HUD_W - 2, 1, G4);
        return im;
    }

    /* =========================================================
       MENU « QUÊTES SUIVIES » (gestion de l'affichage)
       ========================================================= */

    static BufferedImage bgTracking() {
        BufferedImage im = windowBase(61);
        recess(im, M + sx(0), M + sy(0), 126, 18, true);         // bannière centrée (colonnes 0-6)
        socket(im, sx(7), sy(0));
        socket(im, sx(8), sy(0));
        for (int r = 1; r <= 3; r++) for (int c = 0; c < 9; c++) cardWell(im, M + sx(c), M + sy(r), r * 17 + c);
        recess(im, M + sx(0), M + sy(4), 162, 18, true);       // positions
        recess(im, M + sx(0), M + sy(5), 108, 18, true);       // visible / masqué
        recess(im, M + sx(6), M + sy(5), 54, 18, true);        // tous
        return im;
    }

    /**
     * Journal de quêtes, onglet « Récits » : onglets, grille 9 x 3 des personnages, plaque du
     * bilan (rangée 4) et barre des récits accomplis (rangée 5, comme la barre d'expérience).
     */
    static BufferedImage bgJournal() {
        BufferedImage im = windowBase(67);
        recess(im, M + sx(0), M + sy(0), 126, 18, true);         // onglets (colonnes 0-6)
        socket(im, sx(7), sy(0));
        socket(im, sx(8), sy(0));
        for (int r = 1; r <= 3; r++) for (int c = 0; c < 9; c++) cardWell(im, M + sx(c), M + sy(r), r * 23 + c);
        // Rangée 4 : plaque du bilan
        recess(im, M + sx(0), M + sy(4), 162, 18, true);
        rect(im, M + sx(0) + 1, M + sy(4) + 1, 160, 16, G1);
        // Rangée 5 : barre des récits accomplis, médaillon à la plume
        int by = M + sy(5);
        recess(im, M + sx(0), by, 162, 18, true);
        fill(im, M + 27, by + 3, 140, 12, OUT);
        fill(im, M + 28, by + 4, 138, 10, 0xFF241509);
        for (int x = M + 28; x < M + 166; x++) px(im, x, by + 4, 0xFF140C05);
        bevel(im, M + sx(0) + 1, by + 1, 16, 16, PURPLE);
        drawIcon(im, "quill", M + sx(0) + 3, by + 3);
        return im;
    }

    /**
     * Où sont-ils ? (/city locate) : grille des personnages (colonnes 0-2, rangées 1-4) et pages
     * (rangée 5), carte (colonnes 3-8, rangées 1-4 : parchemin de carte vierge sous les pixels
     * dessinés par le plugin, les zones inconnues restent ainsi) et plaque des coordonnées.
     */
    static BufferedImage bgLocate() {
        BufferedImage im = windowBase(71);
        recess(im, M + sx(0), M + sy(0), 126, 18, true);         // bannière (colonnes 0-6)
        socket(im, sx(7), sy(0));
        socket(im, sx(8), sy(0));
        for (int r = 1; r <= 4; r++) for (int c = 0; c < 3; c++) cardWell(im, M + sx(c), M + sy(r), r * 13 + c);
        recess(im, M + sx(0), M + sy(5), 54, 18, true);          // pages
        // Carte : cadre sombre, parchemin de carte vierge à l'intérieur (106 x 70)
        int mx = M + sx(3), my = M + sy(1);
        rect(im, mx, my, 108, 72, OUT);
        parchment(im, mx + 1, my + 1, 106, 70, 73);
        for (int i = mx + 1; i < mx + 107; i++) px(im, i, my + 1, P1);   // ombre du cadre
        for (int j = my + 1; j < my + 71; j++) px(im, mx + 1, j, P1);
        // Plaque des coordonnées (rangée 5)
        recess(im, M + sx(3), M + sy(5), 108, 18, true);
        rect(im, M + sx(3) + 1, M + sy(5) + 1, 106, 16, G1);
        return im;
    }

    /** Aperçu de l'écran « Où sont-ils ? » avec une carte et des marques factices. */
    static void previewLocate(File dir, BufferedImage bg) throws IOException {
        Preview p = new Preview(bg);
        p.textC("OÙ SONT-ILS ?", 88, 3, G3, true);
        p.textC("THORVALD · FORGERON", sx(0) + 63, sy(0) + 4, P4, true);
        p.put(load("ic_map"), sx(7), sy(0)).put(load("ic_close"), sx(8), sy(0));
        for (int k = 0; k < 12; k++) {
            int col = k % 3, row = 1 + k / 3;
            if (k < 8) p.put(load(k == 2 ? "ring_gold" : "ring_green"), sx(col), sy(row)).item(col, row, 0xFFB07850);
            else p.put(load("ring_gray"), sx(col), sy(row)).put(load("lock"), sx(col) + 3, sy(row) + 3);
        }
        p.put(load("ic_prev_off"), sx(0), sy(5)).put(load("ic_next"), sx(2), sy(5));
        // Carte factice : prairie, rivière, ville (bordure dorée), marques
        for (int j = 0; j < 35; j++) for (int i = 0; i < 53; i++) {
            double n = smooth(i, j, 6, 3);
            int c = n < 0.25 ? 0xFF4060C8 : n < 0.3 ? 0xFFD8CC8C : (i + j) % 7 == 0 ? 0xFF5E8A2E : 0xFF6E9C34;
            boolean city = i >= 18 && i < 34 && j >= 10 && j < 26;
            boolean edge = city && (i == 18 || i == 33 || j == 10 || j == 25);
            if (city && !edge && (i * 7 + j * 3) % 11 < 3) c = 0xFF8C7A5A;
            if (edge) c = 0xFFF1CF5E;
            fill(p.im, 20 + 62 + 2 * i, 20 + 36 + 2 * j, 2, 2, c);
        }
        int[][] npcs = {{22, 14}, {29, 20}, {24, 22}};
        for (int[] n : npcs) {
            fill(p.im, 20 + 62 + 2 * (n[0] - 1), 20 + 36 + 2 * (n[1] - 1), 6, 6, OUT);
            fill(p.im, 20 + 62 + 2 * n[0] - 2, 20 + 36 + 2 * n[1], 6, 2, 0xFFD8402F);
            fill(p.im, 20 + 62 + 2 * n[0], 20 + 36 + 2 * n[1] - 2, 2, 6, 0xFFD8402F);
        }
        int sxm = 20 + 62 + 2 * 27, sym = 20 + 36 + 2 * 17;
        fill(p.im, sxm - 4, sym - 4, 10, 10, OUT);
        fill(p.im, sxm - 4, sym, 10, 2, G3); fill(p.im, sxm, sym - 4, 2, 10, G3);
        p.save(new File(dir, "localiser.png"));
    }

    /** Cadre doré de l'option choisie (54 x 18, intérieur transparent). */
    static BufferedImage selected54() {
        BufferedImage im = img(54, 18);
        rect(im, 0, 0, 54, 18, G4);
        rect(im, 1, 1, 52, 16, G2);
        clear(im, 0, 0); clear(im, 53, 0); clear(im, 0, 17); clear(im, 53, 17);
        return im;
    }

    static BufferedImage load(String name) throws IOException {
        return ImageIO.read(new File(uiDir, name + ".png"));
    }

    static void writePreviews(File dir, BufferedImage bgChar, BufferedImage bgList, BufferedImage bgQ,
                              BufferedImage bgP, BufferedImage bgCQ, BufferedImage bgB) throws IOException {
        // Artisan installé
        Preview p = new Preview(bgChar);
        p.textC("THORVALD · FORGERON", 88, 3, G3, true);
        p.text("COMPAGNON", 12, 21, G3, true).put(load("stars_3"), 70, 22);
        p.put(load("ic_job"), sx(6), sy(0)).put(load("ic_follow"), sx(7), sy(0)).put(load("ic_close"), sx(8), sy(0));
        p.put(load("pf_forge"), 11, 39).put(load("silhouette"), 19, 43);
        p.put(load("btn_vendre"), sx(4), sy(1)).put(load("btn_entrepot"), sx(4), sy(2))
                .put(load("btn_boutique"), sx(4), sy(3)).put(load("btn_quetes"), sx(4), sy(4));
        for (int k = 0; k < 41; k++) p.put(load("seg_xp"), 28 + 2 * k, sy(5) + 4);
        p.textC("1 240 / 2 000 XP", 97, sy(5) + 3, P4, true);
        p.save(new File(dir, "artisan.png"));

        // Vente
        p = new Preview(bgList);
        p.textC("THORVALD · FORGERON", 88, 3, G3, true);
        p.put(load("ic_back"), sx(0), sy(0)).put(load("rib_vendre"), sx(1), sy(0))
                .put(load("ic_info"), sx(7), sy(0)).put(load("ic_close"), sx(8), sy(0));
        int[] colors = {0xFF9C9C9C, 0xFF5A3A1A, 0xFFE0C060, 0xFF60C0E0, 0xFF303030, 0xFFB08040, 0xFFCC4444};
        String[] rings = {"ring_green", "ring_green", "ring_amber", "ring_red", "ring_green", "ring_amber", "ring_gray"};
        for (int i = 0; i < colors.length; i++) {
            p.put(load(rings[i]), sx(i), sy(1)).item(i, 1, colors[i]);
        }
        p.put(load("ic_prev_off"), sx(0), sy(5)).put(load("ic_next_off"), sx(8), sy(5));
        p.put(load("coin"), 30, sy(5) + 5).text("BOURSE : 1 250", 42, sy(5) + 3, G3, true);
        p.save(new File(dir, "vente.png"));

        // Boutique (articles verrouillés)
        p = new Preview(bgList);
        p.textC("THORVALD · FORGERON", 88, 3, G3, true);
        p.put(load("ic_back"), sx(0), sy(0)).put(load("rib_boutique"), sx(1), sy(0))
                .put(load("ic_info"), sx(7), sy(0)).put(load("ic_close"), sx(8), sy(0));
        for (int i = 0; i < 5; i++) p.put(load(i < 3 ? "ring_green" : "ring_red"), sx(i), sy(1)).item(i, 1, colors[i]);
        for (int i = 5; i < 9; i++) p.put(load("ring_gray"), sx(i), sy(1)).put(load("lock"), sx(i) + 3, sy(1) + 3);
        p.put(load("ic_prev_off"), sx(0), sy(5)).put(load("ic_next_off"), sx(8), sy(5));
        p.put(load("coin"), 30, sy(5) + 5).text("BOURSE : 1 250", 42, sy(5) + 3, G3, true);
        p.save(new File(dir, "boutique.png"));

        // Quêtes d'un PNJ
        p = new Preview(bgQ);
        p.textC("THORVALD · FORGERON", 88, 3, G3, true);
        p.put(load("ic_back"), sx(0), sy(0)).put(load("rib_quetes_pnj"), sx(1), sy(0)).put(load("ic_close"), sx(8), sy(0));
        p.text("PRINCIPALE", sx(0) + 4, sy(1) + 4, P4, true).put(load("coin"), sx(0) + 50, sy(1) + 5).text("48", sx(0) + 60, sy(1) + 4, G3, true);
        p.item(0, 2, 0xFF9C9C9C).text("12/32", sx(1) + 3, sy(2) + 1, INK, false);
        for (int k = 0; k < 9; k++) p.put(load("seg_green"), sx(1) + 4 + 2 * k, sy(2) + 12);
        p.item(0, 3, 0xFF303030).text("0/16", sx(1) + 3, sy(3) + 1, INK, false);
        p.put(load("qb_valider"), sx(0), sy(4));
        p.text("SPÉCIALE", sx(5) + 4, sy(1) + 4, P4, true).put(load("coin"), sx(5) + 50, sy(1) + 5).text("96", sx(5) + 60, sy(1) + 4, G3, true);
        p.put(load("qb_repos"), sx(5), sy(4));
        p.put(load("vb_recits"), sx(4), sy(1));
        p.save(new File(dir, "quetes_pnj.png"));

        // Récits
        p = new Preview(bgP);
        p.textC("THORVALD · FORGERON", 88, 3, G3, true);
        p.put(load("ic_back"), sx(0), sy(0)).put(load("rib_recits"), sx(1), sy(0)).put(load("ic_close"), sx(8), sy(0));
        String[] names = {"LE FER DU NORD", "FORGER UNE LAME", "LE MAÎTRE ET L'ENCLUME", "SOUS LA MONTAGNE", "LA LAME ROYALE"};
        for (int r = 1; r <= 5; r++) {
            p.item(0, r, colors[r]).text(names[r - 1], sx(1) + 4, sy(r) + 1, INK, false);
            int n = r == 1 ? 49 : r == 2 ? 20 : 0;
            for (int k = 0; k < n; k++) p.put(load("seg_thin"), sx(1) + 5 + 2 * k, sy(r) + 13);
            p.put(load(r == 1 ? "badge_check" : r == 2 ? "badge_progress" : "badge_new"), sx(8) + 4, sy(r) + 4);
        }
        p.save(new File(dir, "recits.png"));

        // Maire
        p = new Preview(bgChar);
        p.textC("ALDERIC · MAIRE DE VALMONT", 88, 3, G3, true);
        p.text("VILLAGE", 12, 21, G3, true).text("NIV. 3", 70, 21, P3, true);
        p.put(load("ic_story"), sx(6), sy(0)).put(load("ic_follow"), sx(7), sy(0)).put(load("ic_close"), sx(8), sy(0));
        p.put(load("pf_mairie"), 11, 39).put(load("silhouette"), 19, 43);
        p.put(load("btn_quetes"), sx(4), sy(1)).put(load("btn_batiments"), sx(4), sy(2))
                .put(load("btn_tresor"), sx(4), sy(3)).put(load("btn_agrandir"), sx(4), sy(4));
        for (int k = 0; k < 50; k++) p.put(load("seg_xp"), 28 + 2 * k, sy(5) + 4);
        p.textC("PARCELLES 18 / 25", 97, sy(5) + 3, P4, true);
        p.save(new File(dir, "maire.png"));

        // Quêtes de la cité
        p = new Preview(bgCQ);
        p.textC("ALDERIC · MAIRE DE VALMONT", 88, 3, G3, true);
        p.put(load("ic_back"), sx(0), sy(0)).put(load("rib_quetes"), sx(1), sy(0)).put(load("ic_close"), sx(8), sy(0));
        for (int i = 0; i < 6; i++) {
            p.put(load(i < 2 ? "ring_green" : i < 5 ? "ring_amber" : "ring_purple"), sx(i), sy(1)).item(i, 1, colors[i]);
            if (i < 2) p.put(load("badge_check"), sx(i) + 10, sy(1) - 1);
        }
        p.put(load("fb_recit"), sx(0), sy(5)).put(load("fb_charte_lock"), sx(3), sy(5));
        p.save(new File(dir, "quetes_cite.png"));

        // Bâtiments
        p = new Preview(bgB);
        p.textC("ALDERIC · MAIRE DE VALMONT", 88, 3, G3, true);
        p.put(load("ic_back"), sx(0), sy(0)).put(load("rib_batiments"), sx(1), sy(0)).put(load("ic_close"), sx(8), sy(0));
        for (int i = 0; i < 4; i++) p.put(load("ring_gold"), sx(i), sy(1)).item(i, 1, 0xFFB05030);
        p.put(load("fb_ajouter"), sx(0), sy(5)).put(load("fb_supprimer"), sx(5), sy(5));
        p.save(new File(dir, "batiments.png"));
    }
}
