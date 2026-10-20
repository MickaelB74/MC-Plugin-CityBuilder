// Table de la police par défaut de Minecraft pour les dialogues sur deux lignes (ui/UiDialogue).
//
// Usage (depuis la racine du projet, après un build du mod compagnon, qui télécharge le jeu) :
//     java tools/dialogue-font/DialogueFont.java companions/citycore-companion/build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar
//
// Lit la police « include/default » du jeu (fournisseurs bitmap : nonlatin_european, accented,
// ascii) et calcule l'avance de chaque caractère comme le jeu : largeur réelle du glyphe (dernière
// colonne non transparente de sa case) × échelle, + 1.
//
// Produit src/main/resources/ui/dialogue_font.json :
//   - "providers" : les fournisseurs bitmap (fichier, hauteur, ascent, grille de caractères), que
//     le plugin recopie dans le pack en abaissant l'ascent pour la deuxième ligne ;
//   - "advances"  : avance en pixels de chaque caractère (le premier fournisseur qui l'a gagne).
// Aucune image n'est copiée : le pack renvoie aux textures du jeu du joueur.

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public class DialogueFont {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage : java tools/dialogue-font/DialogueFont.java <jar des ressources du client>");
            System.exit(1);
        }
        Map<String, Integer> advances = new LinkedHashMap<>();
        StringBuilder providers = new StringBuilder();
        try (ZipFile jar = new ZipFile(args[0])) {
            String json = new String(jar.getInputStream(jar.getEntry("assets/minecraft/font/include/default.json"))
                    .readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) ((Map<String, Object>) new Json(json).value()).get("providers");
            for (Object o : list) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) o;
                if (!"bitmap".equals(p.get("type"))) continue;
                @SuppressWarnings("unchecked")
                List<String> rows = (List<String>) (List<?>) p.get("chars");
                String file = (String) p.get("file");
                int height = p.containsKey("height") ? ((Number) p.get("height")).intValue() : 8;
                int ascent = ((Number) p.get("ascent")).intValue();

                String[] ref = file.split(":", 2);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(jar.getInputStream(
                        jar.getEntry("assets/" + ref[0] + "/textures/" + ref[1])).readAllBytes()));
                int cols = rows.get(0).codePointCount(0, rows.get(0).length());
                int cw = img.getWidth() / cols, ch = img.getHeight() / rows.size();
                double scale = (double) height / ch;
                for (int r = 0; r < rows.size(); r++) {
                    int[] cps = rows.get(r).codePoints().toArray();
                    for (int c = 0; c < cps.length; c++) {
                        String key = new String(Character.toChars(cps[c]));
                        if (cps[c] == 0 || advances.containsKey(key)) continue;
                        advances.put(key, (int) (0.5 + glyphWidth(img, c, r, cw, ch) * scale) + 1);
                    }
                }
                if (providers.length() > 0) providers.append(',');
                providers.append("{\"file\":").append(str(file)).append(",\"height\":").append(height)
                        .append(",\"ascent\":").append(ascent).append(",\"chars\":[");
                for (int r = 0; r < rows.size(); r++) {
                    if (r > 0) providers.append(',');
                    providers.append(str(rows.get(r)));
                }
                providers.append("]}");
            }
        }
        advances.put(" ", 4); // fournisseur « space » de la police par défaut

        StringBuilder out = new StringBuilder("{\"providers\":[").append(providers).append("],\"advances\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : advances.entrySet()) {
            if (!first) out.append(',');
            out.append(str(e.getKey())).append(':').append(e.getValue());
            first = false;
        }
        out.append("}}");
        Path target = Path.of("src/main/resources/ui/dialogue_font.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
        System.out.println(target + " : " + advances.size() + " caractères");
    }

    /** Largeur réelle d'un glyphe : dernière colonne contenant un pixel non transparent, + 1. */
    static int glyphWidth(BufferedImage img, int col, int row, int cw, int ch) {
        for (int x = cw - 1; x >= 0; x--) {
            for (int y = 0; y < ch; y++) {
                if ((img.getRGB(col * cw + x, row * ch + y) >>> 24) != 0) return x + 1;
            }
        }
        return 0;
    }

    /** Chaîne JSON, tout caractère hors ASCII imprimable échappé (\\uXXXX). */
    static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c >= 0x20 && c < 0x7F) sb.append(c);
            else sb.append(String.format("\\u%04x", (int) c));
        }
        return sb.append('"').toString();
    }

    /** Lecteur JSON minimal (objets, tableaux, chaînes, nombres, booléens). */
    static final class Json {
        private final String s;
        private int i;

        Json(String s) { this.s = s; }

        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return true; }
            if (s.startsWith("false", i)) { i += 5; return false; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                i++; // :
                m.put(k, value());
                ws();
                if (s.charAt(i++) == '}') return m;
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                if (s.charAt(i++) == ']') return l;
            }
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    default -> sb.append(e);
                }
            }
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}
