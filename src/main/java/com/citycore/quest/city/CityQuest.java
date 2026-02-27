package com.citycore.quest.city;

import com.citycore.npc.CityNPC;
import org.bukkit.Material;

public record CityQuest(
        String        id,
        String        displayName,
        String        description,
        Material      icon,
        CityQuestType type,
        int           targetValue,
        CityNPC       targetNpc,
        int           spawnRadius
) {
    /** Constructeur standard sans NPC */
    public static CityQuest standard(String id, String displayName, String description,
                                     Material icon, CityQuestType type, int targetValue) {
        return new CityQuest(id, displayName, description, icon, type, targetValue, null, 0);
    }

    /** Constructeur FIND_NPC */
    public static CityQuest findNpc(String id, String displayName, String description,
                                    Material icon, CityNPC targetNpc, int spawnRadius) {
        return new CityQuest(id, displayName, description, icon,
                CityQuestType.FIND_NPC, 1, targetNpc, spawnRadius);
    }
}