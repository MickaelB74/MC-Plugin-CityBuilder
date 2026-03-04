package com.citycore.quest;

public enum QuestType {
    COLLECT_ITEMS,    // Ramener X items
    KILL_ENTITIES,    // Tuer X entités
    CRAFT_ITEMS,      // Crafter X items
    EXPLORE_CHUNK,    // Explorer N chunks d'un biome donné
    PERSONAL_QUEST    // Quête personnalisée avec validator custom
}