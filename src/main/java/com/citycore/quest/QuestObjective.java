package com.citycore.quest;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Un objectif individuel dans une quête.
 * Soit material (COLLECT/CRAFT), soit entity (KILL).
 */
public record QuestObjective(
        String id,           // ex: "STONE_128" ou "CREEPER_10"
        Material material,   // null si KILL
        EntityType entity,   // null si COLLECT/CRAFT
        int amount
) {
    public static QuestObjective ofMaterial(Material mat, int amount) {
        return new QuestObjective(mat.name() + "_" + amount, mat, null, amount);
    }

    public static QuestObjective ofEntity(EntityType entity, int amount) {
        return new QuestObjective(entity.name() + "_" + amount, null, entity, amount);
    }

    public boolean isMaterialObjective() { return material != null; }
    public boolean isEntityObjective()   { return entity != null; }
}