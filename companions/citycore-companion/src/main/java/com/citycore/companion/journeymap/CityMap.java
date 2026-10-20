package com.citycore.companion.journeymap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenu de {@code plugins/CityCore/carte.json}, écrit par le plugin CityCore (classe
 * {@code CityMapExport}) : nom de la ville, style du tracé et chunks revendiqués par dimension.
 * Classe sans dépendance à JourneyMap.
 *
 * @param name      texte affiché sur la zone
 * @param info      bulle au survol
 * @param color     couleur 0xRRGGBB (remplissage et contour)
 * @param fill      opacité du remplissage de chaque parcelle (0 à 1)
 * @param stroke    opacité du contour extérieur (0 à 1)
 * @param width     épaisseur du contour extérieur, en pixels
 * @param minimap   zone visible aussi sur la minicarte (sinon carte plein écran seulement)
 * @param grid      quadrillage des parcelles (null : pas de quadrillage)
 * @param zones     dimension (« minecraft:overworld ») → chunks {x, z}
 */
record CityMap(String name, String info, int color, float fill, float stroke, float width, boolean minimap,
               Grid grid, Map<String, List<long[]>> zones) {

    /** Quadrillage : contour fin de chaque parcelle, sous le contour de la ville. */
    record Grid(int color, float opacity, float width) {}

    /** Couleur par défaut : l'or de Valmont (interfaces, titres). */
    static final int DEFAULT_COLOR = 0xF1CF5E;

    /** Lecture tolérante : valeurs absentes → valeurs par défaut du plugin (format 1 compris). */
    static CityMap parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String name = string(o, "nom", "Ville");
        String info = string(o, "info", name);
        int color = color(string(o, "couleur", ""), DEFAULT_COLOR);

        Grid grid = null;
        if (o.has("quadrillage") && o.get("quadrillage").isJsonObject()) {
            JsonObject g = o.getAsJsonObject("quadrillage");
            if (bool(g, "actif", true)) {
                grid = new Grid(color(string(g, "couleur", ""), color),
                        number(g, "opacite", 0.35f), number(g, "epaisseur", 1f));
            }
        }

        Map<String, List<long[]>> zones = new LinkedHashMap<>();
        if (o.has("zones") && o.get("zones").isJsonArray()) {
            for (JsonElement z : o.getAsJsonArray("zones")) {
                JsonObject zone = z.getAsJsonObject();
                List<long[]> chunks = new ArrayList<>();
                for (JsonElement c : zone.getAsJsonArray("chunks")) {
                    JsonArray xz = c.getAsJsonArray();
                    chunks.add(new long[]{xz.get(0).getAsLong(), xz.get(1).getAsLong()});
                }
                if (!chunks.isEmpty()) zones.put(string(zone, "dimension", "minecraft:overworld"), chunks);
            }
        }
        return new CityMap(name, info, color,
                number(o, "remplissage", 0.15f), number(o, "bordure", 0.9f), number(o, "epaisseur", 2f),
                bool(o, "minicarte", true), grid, zones);
    }

    /** « F1CF5E » ou « #F1CF5E » → 0xF1CF5E ; vide ou invalide → valeur donnée. */
    private static int color(String raw, int fallback) {
        String c = raw.trim().replace("#", "");
        if (c.isEmpty()) return fallback;
        try {
            return Integer.parseInt(c, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static float number(JsonObject o, String key, float fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : fallback;
    }
}
