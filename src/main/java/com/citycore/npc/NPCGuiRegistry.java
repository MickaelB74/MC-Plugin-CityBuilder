package com.citycore.npc;

import java.util.HashMap;
import java.util.Map;

public class NPCGuiRegistry {

    private final Map<CityNPC, NPCGui> guis = new HashMap<>();

    public void register(CityNPC type, NPCGui gui) {
        guis.put(type, gui);
    }

    public NPCGui get(CityNPC type) {
        return guis.get(type);
    }

    /** Retrouve le GUI dont le titre correspond (pour les clicks inventaire). */
    public NPCGui getByTitle(String title) {
        return guis.values().stream()
                .filter(gui -> gui.getTitle().equals(title))
                .findFirst()
                .orElse(null);
    }
}