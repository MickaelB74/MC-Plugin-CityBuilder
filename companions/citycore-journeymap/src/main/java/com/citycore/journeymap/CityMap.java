package com.citycore.journeymap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenu de {@code plugins/CityCore/carte.json}, écrit par le plugin CityCore : nom de la ville,
 * style du tracé et chunks revendiqués par dimension.
 *
 * @param name   texte affiché sur la zone
 * @param info   bulle au survol
 * @param color  couleur 0xRRGGBB
 * @param fill   opacité de l'intérieur (0 à 1)
 * @param stroke opacité du contour (0 à 1)
 * @param width  épaisseur du contour, en pixels
 * @param zones  dimension (« minecraft:overworld ») → chunks {x, z}
 */
record CityMap(String name, String info, int color, float fill, float stroke, float width,
               Map<String, List<long[]>> zones) {

    /** Lecture tolérante : valeurs absentes → valeurs par défaut du plugin. */
    static CityMap parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String name = string(o, "nom", "Ville");
        String info = string(o, "info", name);
        int color;
        try {
            color = Integer.parseInt(string(o, "couleur", "F1CF5E").replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            color = 0xF1CF5E;
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
                number(o, "remplissage", 0.15f), number(o, "bordure", 0.9f), number(o, "epaisseur", 2f), zones);
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static float number(JsonObject o, String key, float fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : fallback;
    }
}
