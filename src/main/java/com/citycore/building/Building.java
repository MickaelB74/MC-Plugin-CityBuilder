package com.citycore.building;

public record Building(
        int id,
        String name,
        String world,
        int x1, int z1,
        int x2, int z2,
        String npcTag   // null si aucun NPC assigné
) {
    public String npcDisplayTag() {
        return npcTag != null ? npcTag.replace("citycore_", "") : "Aucun";
    }
}