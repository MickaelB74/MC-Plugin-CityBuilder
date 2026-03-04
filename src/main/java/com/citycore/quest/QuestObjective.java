package com.citycore.quest;

import org.bukkit.block.Biome;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Un objectif individuel dans une quête.
 * Soit material (COLLECT/CRAFT), soit entity (KILL), soit biome (EXPLORE_CHUNK),
 * soit personal (PERSONAL_QUEST — identifié par personalId).
 */
public record QuestObjective(
        String id,            // ex: "STONE_128", "CREEPER_10", "DESERT_3", "SUMMIT_1"
        Material material,    // null sauf COLLECT/CRAFT
        EntityType entity,    // null sauf KILL
        Biome biome,          // null sauf EXPLORE_CHUNK
        String personalId,    // null sauf PERSONAL_QUEST (ex: "reach_summit")
        int amount
) {
    // ── Factories existantes ─────────────────────────────────────────

    public static QuestObjective ofMaterial(Material mat, int amount) {
        return new QuestObjective(mat.name() + "_" + amount, mat, null, null, null, amount);
    }

    public static QuestObjective ofEntity(EntityType entity, int amount) {
        return new QuestObjective(entity.name() + "_" + amount, null, entity, null, null, amount);
    }

    // ── Nouvelles factories ──────────────────────────────────────────

    /**
     * Objectif "explorer N chunks d'un biome donné".
     * L'id est au format "BIOME_NAME_N" pour éviter les collisions.
     */
    public static QuestObjective ofBiome(Biome biome, int amount) {
        return new QuestObjective(biome.name() + "_" + amount, null, null, biome, null, amount);
    }

    /**
     * Objectif lié à une quête personnelle (validator externe).
     * L'amount est typiquement 1 (quête one-shot).
     */
    public static QuestObjective ofPersonal(String personalId, int amount) {
        return new QuestObjective("PERSONAL_" + personalId + "_" + amount,
                null, null, null, personalId, amount);
    }

    // ── Prédicats de type ────────────────────────────────────────────

    public boolean isMaterialObjective() { return material != null; }
    public boolean isEntityObjective()   { return entity   != null; }
    public boolean isBiomeObjective()    { return biome    != null; }
    public boolean isPersonalObjective() { return personalId != null; }
}