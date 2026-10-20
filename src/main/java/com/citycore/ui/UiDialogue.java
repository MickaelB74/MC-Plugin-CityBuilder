package com.citycore.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Répliques des PNJ sur deux lignes dans la barre d'action, pour les joueurs équipés du pack.
 *
 * La barre d'action n'affiche qu'une ligne. Chaque ligne est écrite dans une copie de la police
 * classique du jeu, la deuxième abaissée de {@link #LINE_STEP} px ({@code citycore:dialogue_0} et
 * {@code citycore:dialogue_1} : mêmes textures que la police par défaut, rien n'est copié). Le jeu
 * refuse d'élever une police (ascent plus grand que la hauteur) : on ne peut que descendre, et deux
 * lignes tiennent entre la barre d'action et les cœurs / l'armure. Une réplique plus longue passe
 * sur une page suivante de deux lignes.
 *
 * La première ligne a aussi sa copie : la police par défaut du client peut être remplacée (police
 * Unicode forcée, pack ou mod de polices), et ses largeurs ne seraient plus celles de la table.
 *
 * Mise en page d'une machine à écrire : les lignes partent du même bord gauche et les lettres
 * apparaissent l'une après l'autre ; le bloc est centré sur sa largeur définitive (la plus longue
 * ligne de la page), si bien que le début du texte ne bouge pas pendant l'écriture. Une ligne
 * s'arrête à la largeur maximale (dialogues.largeur-pixels) et la suite passe à la ligne. Largeurs
 * des glyphes : ui/dialogue_font.json (tirées du jeu par tools/dialogue-font). Une réplique
 * contenant un caractère hors de cette police (émoji…) garde l'affichage en morceaux d'une ligne.
 */
public final class UiDialogue {

    /** Lignes par page (au-delà, la troisième passerait sur l'armure et les cœurs). */
    public static final int LINES = 2;
    /** Écart entre deux lignes (hauteur d'une ligne de la police du jeu + 1). */
    static final int LINE_STEP = 10;
    /** Police de la deuxième ligne. */
    static final Key LINE2_FONT = Key.key(UiAtlas.NS, "dialogue_1");

    /** Police de la ligne {@code index} (0 = première) : copie de la police du jeu, abaissée d'autant de lignes. */
    static Key lineFont(int index) {
        return Key.key(UiAtlas.NS, "dialogue_" + index);
    }

    private static final String RESOURCE = "ui/dialogue_font.json";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    /** Curseur de la machine à écrire (présent dans la police du jeu, donc décalable). */
    private static final String CURSOR = "▌";

    private final UiAtlas atlas;
    private final UiPack  pack;
    /** Avance (pixels) de chaque caractère de la police du jeu. */
    private final Map<Integer, Integer> advances = new HashMap<>();
    /** Largeur maximale d'une ligne, en pixels d'interface. */
    private int maxWidth = 400;

    public UiDialogue(JavaPlugin plugin, UiAtlas atlas, UiPack pack) throws IOException {
        this.atlas = atlas;
        this.pack  = pack;
        JsonObject table = read(plugin);
        for (Map.Entry<String, JsonElement> e : table.getAsJsonObject("advances").entrySet()) {
            advances.put(e.getKey().codePointAt(0), e.getValue().getAsInt());
        }
    }

    public void setMaxWidth(int pixels) {
        maxWidth = Math.max(120, pixels);
    }

    /** Le joueur voit-il les répliques sur deux lignes (pack chargé) ? */
    public boolean supports(Player player) {
        return pack.isLoaded(player);
    }

    /* =========================
       POLICE DU PACK
       ========================= */

    /**
     * Polices à ajouter au pack : une copie de la police du jeu par ligne, abaissée de son rang
     * (chemin → contenu). Vide si la table manque (le pack se construit quand même, les répliques
     * restent sur une ligne).
     */
    static Map<String, JsonObject> packFonts(JavaPlugin plugin) {
        Map<String, JsonObject> fonts = new LinkedHashMap<>();
        JsonObject table;
        try {
            table = read(plugin);
        } catch (IOException e) {
            plugin.getLogger().warning("⚠ " + RESOURCE + " illisible (" + e.getMessage()
                    + ") : répliques des PNJ sur une ligne.");
            return fonts;
        }
        for (int line = 0; line < LINES; line++) {
            JsonArray providers = new JsonArray();
            JsonObject space = new JsonObject();
            space.addProperty("type", "reference");
            space.addProperty("id", "minecraft:include/space");
            providers.add(space);
            for (JsonElement el : table.getAsJsonArray("providers")) {
                JsonObject src = el.getAsJsonObject();
                JsonObject p = new JsonObject();
                p.addProperty("type", "bitmap");
                p.addProperty("file", src.get("file").getAsString());
                p.addProperty("height", src.get("height").getAsInt());
                p.addProperty("ascent", src.get("ascent").getAsInt() - line * LINE_STEP);
                p.add("chars", src.get("chars"));
                providers.add(p);
            }
            JsonObject font = new JsonObject();
            font.add("providers", providers);
            Key key = lineFont(line);
            fonts.put("assets/" + key.namespace() + "/font/" + key.value() + ".json", font);
        }
        return fonts;
    }

    private static JsonObject read(JavaPlugin plugin) throws IOException {
        try (InputStream in = plugin.getResource(RESOURCE)) {
            if (in == null) throw new IOException(RESOURCE + " absent du plugin");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /* =========================
       MISE EN PAGE
       ========================= */

    /**
     * Pages d'une réplique (lignes séparées par « \n »), coupées entre deux mots ; le nom du
     * personnage (« Thorvald : ») ouvre chaque page, la mise en forme en cours (couleur,
     * italique) est reprise en tête de chaque ligne, « … » termine une page non finale.
     *
     * @return null si un caractère manque à la police du jeu (affichage en morceaux d'une ligne)
     */
    public List<String> pages(String line) {
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            if (cp == '§' && i + 1 < line.length()) { i += 2; continue; }
            if (!advances.containsKey(cp)) return null;
            i += Character.charCount(cp);
        }
        int colon = line.indexOf(": ");
        String speaker = colon > 0 && colon < 40 ? line.substring(0, colon + 2) : "";
        String body = line.substring(speaker.length());

        List<String> pages = new ArrayList<>();
        List<String> page = new ArrayList<>();
        String active = codesAfter("", speaker);   // mise en forme en cours au fil du texte
        StringBuilder current = new StringBuilder(speaker);
        int width = width(speaker, "");
        boolean empty = true;                      // aucune parole encore sur la ligne
        for (String word : body.split(" ", -1)) {
            int w = width(word, active);
            int space = advance(' ', active);
            if (!empty && width + space + w > maxWidth) {
                page.add(current.toString());
                if (page.size() == LINES) {
                    // Page pleine : « … », puis nouvelle page ouverte par le nom du personnage
                    int last = page.size() - 1;
                    page.set(last, page.get(last) + " §8…");
                    pages.add(String.join("\n", page));
                    page.clear();
                    current = new StringBuilder(speaker).append(active);
                    width = width(speaker, "");
                } else {
                    current = new StringBuilder(active);
                    width = 0;
                }
                empty = true;
            }
            if (!empty) { current.append(' '); width += space; }
            current.append(word);
            width += w;
            active = codesAfter(active, word);
            if (!word.replaceAll("§.", "").isEmpty()) empty = false;
        }
        page.add(current.toString());
        pages.add(String.join("\n", page));
        return pages;
    }

    /** Nombre de caractères visibles d'une page (codes « §x » et sauts de ligne exclus). */
    public static int visibleLength(String page) {
        int n = 0;
        for (int i = 0; i < page.length(); i++) {
            char c = page.charAt(i);
            if (c == '§' && i + 1 < page.length()) { i++; continue; }
            if (c != '\n') n++;
        }
        return n;
    }

    /* =========================
       RENDU
       ========================= */

    /**
     * Page affichée jusqu'au caractère visible {@code revealed} (machine à écrire) : les lignes
     * partent du même bord gauche, les lettres s'ajoutent à droite ; curseur « ▌ » tant que la page
     * n'est pas entière. Le bloc garde sa largeur définitive (la plus longue ligne de la page) : le
     * jeu, qui centre la barre d'action sur l'avance totale, ne le déplace pas pendant l'écriture.
     */
    public Component render(String page, int revealed) {
        String[] lines = page.split("\n", -1);
        int block = 0;
        for (String line : lines) block = Math.max(block, width(line, ""));
        boolean typing = revealed < visibleLength(page);

        TextComponent.Builder root = Component.text();
        int cursor = 0;
        int left = revealed;
        for (int i = 0; i < lines.length && i < LINES && left > 0; i++) {
            shift(root, -cursor); // retour au bord gauche du bloc
            String shown = prefix(lines[i], left);
            left -= visibleLength(shown);
            boolean last = left <= 0 || i == lines.length - 1;
            if (typing && last) shown += "§7" + CURSOR;
            // Chaque ligne dans sa copie de la police du jeu : largeurs connues, quelle que soit
            // la police par défaut du client
            root.append(Component.text().font(lineFont(i)).append(LEGACY.deserialize(shown)).build());
            cursor = width(shown, "");
        }
        // Fin du bloc à sa largeur définitive, place du curseur comprise (il peut dépasser la plus
        // longue ligne) : l'avance totale reste la même du premier caractère à la fin de la page
        shift(root, block + width(CURSOR, "") - cursor);
        return root.build();
    }

    private void shift(TextComponent.Builder root, int pixels) {
        if (pixels != 0) root.append(Component.text(atlas.shift(pixels)).font(UiAtlas.UI_FONT));
    }

    /** Début d'une ligne contenant {@code visible} caractères visibles (codes compris). */
    private static String prefix(String line, int visible) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '§' && i + 1 < line.length()) { i++; continue; }
            if (n == visible) return line.substring(0, i);
            n++;
        }
        return line;
    }

    /* =========================
       LARGEURS
       ========================= */

    /** Largeur affichée d'un texte, en partant de la mise en forme {@code active} (gras : +1 par caractère). */
    int width(String text, String active) {
        boolean bold = active.contains("§l");
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp == '§' && i + 1 < text.length()) {
                char c = Character.toLowerCase(text.charAt(i + 1));
                if (c == 'l') bold = true;
                else if ("0123456789abcdefr".indexOf(c) >= 0) bold = false;
                i += 2;
                continue;
            }
            w += advances.getOrDefault(cp, 0) + (bold ? 1 : 0);
            i += Character.charCount(cp);
        }
        return w;
    }

    private int advance(char c, String active) {
        return advances.getOrDefault((int) c, 0) + (active.contains("§l") ? 1 : 0);
    }

    /** Codes de mise en forme actifs après un texte : une couleur remet à zéro, un style s'ajoute. */
    private static String codesAfter(String active, String text) {
        String codes = active;
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '§') continue;
            char c = Character.toLowerCase(text.charAt(i + 1));
            String code = "§" + c;
            if ("0123456789abcdefr".indexOf(c) >= 0) codes = c == 'r' ? "" : code;
            else if ("klmno".indexOf(c) >= 0 && !codes.contains(code)) codes += code;
            i++;
        }
        return codes;
    }
}
