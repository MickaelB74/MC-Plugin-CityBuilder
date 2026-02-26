package com.citycore.building;

public record Building(
        int id,
        String name,
        String world,
        int x1, int z1,
        int x2, int z2,
        String npcTag,
        Double npcX,
        Double npcY,
        Double npcZ,
        Float npcYaw
) {
    public boolean hasNpcPoint() {
        return npcX != null && npcY != null && npcZ != null;
    }

    public String npcDisplayTag() {
        return npcTag != null ? npcTag.replace("citycore_", "") : "Aucun";
    }
}