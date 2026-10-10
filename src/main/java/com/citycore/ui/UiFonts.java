package com.citycore.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fichiers de police du pack (assets/citycore/font/*.json), générés à partir du registre.
 *
 * - {@code ui.json} : espacements + fonds de fenêtre (haut de l'image à y = -4) ;
 * - {@code y<N>.json} : espacements, texte plat et images affichées à la hauteur N ;
 * - {@code s<N>.json} : espacements et texte ombré à la hauteur N.
 */
public final class UiFonts {

    private UiFonts() {}

    /** Chemin dans le pack → contenu JSON. */
    public static Map<String, JsonObject> generate(UiAtlas atlas) {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put("assets/citycore/font/ui.json", uiFont(atlas));
        Set<Integer> ys = new TreeSet<>(atlas.spritesAtY().keySet());
        ys.addAll(atlas.flatTextY());
        for (int y : ys) files.put("assets/citycore/font/y" + y + ".json", heightFont(atlas, y, false));
        for (int y : atlas.shadowTextY()) files.put("assets/citycore/font/s" + y + ".json", heightFont(atlas, y, true));
        for (int y : atlas.smallTextY()) files.put("assets/citycore/font/t" + y + ".json", smallFont(atlas, y));
        return files;
    }

    /** Petite police à la hauteur y (pas d'ombre dessinée : le HUD et le Tab en ajoutent une). */
    private static JsonObject smallFont(UiAtlas atlas, int y) {
        JsonArray providers = new JsonArray();
        providers.add(spaceProvider(true));
        List<String> rows = new ArrayList<>();
        for (String row : atlas.smallRows()) {
            StringBuilder r = new StringBuilder(row);
            while (r.length() < 16) r.append('\u0000');
            rows.add(r.toString());
        }
        providers.add(bitmap("citycore:ui/font_small.png", UiAtlas.ASCENT_ORIGIN - y, UiAtlas.SMALL_CELL_H, rows));
        return font(providers);
    }

    private static JsonObject uiFont(UiAtlas atlas) {
        JsonArray providers = new JsonArray();
        providers.add(spaceProvider(false));
        for (String bg : atlas.backgrounds()) {
            UiAtlas.Sprite s = atlas.sprite(bg);
            providers.add(bitmap("citycore:ui/" + bg + ".png", UiAtlas.ASCENT_ORIGIN + 4, s.height(),
                    List.of(String.valueOf(s.ch()))));
        }
        return font(providers);
    }

    private static JsonObject heightFont(UiAtlas atlas, int y, boolean shadow) {
        JsonArray providers = new JsonArray();
        providers.add(spaceProvider(true));
        int ascent = UiAtlas.ASCENT_ORIGIN - y;
        boolean text = shadow ? atlas.shadowTextY().contains(y) : atlas.flatTextY().contains(y);
        if (text) {
            List<String> rows = new ArrayList<>();
            for (String row : atlas.fontRows()) {
                StringBuilder r = new StringBuilder(row);
                while (r.length() < 16) r.append('\u0000'); // lignes de longueur égale
                rows.add(r.toString());
            }
            providers.add(bitmap("citycore:ui/" + (shadow ? "font_shadow" : "font") + ".png",
                    ascent, UiAtlas.TEXT_CELL, rows));
        }
        if (!shadow) {
            for (String name : atlas.spritesAtY().getOrDefault(y, Set.of())) {
                UiAtlas.Sprite s = atlas.sprite(name);
                providers.add(bitmap("citycore:ui/" + name + ".png", ascent, s.height(),
                        List.of(String.valueOf(s.ch()))));
            }
        }
        return font(providers);
    }

    private static JsonObject font(JsonArray providers) {
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        return font;
    }

    private static JsonObject spaceProvider(boolean withSpace) {
        JsonObject advances = new JsonObject();
        if (withSpace) advances.addProperty(" ", 4);
        UiAtlas.shiftAdvances().forEach(advances::addProperty);
        JsonObject p = new JsonObject();
        p.addProperty("type", "space");
        p.add("advances", advances);
        return p;
    }

    private static JsonObject bitmap(String file, int ascent, int height, List<String> chars) {
        JsonObject p = new JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", file);
        p.addProperty("ascent", ascent);
        p.addProperty("height", height);
        JsonArray arr = new JsonArray();
        chars.forEach(arr::add);
        p.add("chars", arr);
        return p;
    }
}
