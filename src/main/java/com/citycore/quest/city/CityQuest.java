package com.citycore.quest.city;

import com.citycore.npc.CityNPC;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Quête de ville.
 *
 * La description peut contenir {n}, remplacé par l'objectif effectif
 * (qui peut être surchargé dans config.yml : city-quests.targets.<id>).
 *
 * @param materials   matériaux acceptés (DELIVER_ITEMS) ou matériau suivi (STOCK_LEVEL)
 * @param entity      créature à tuer (KILL_MOBS), null = n'importe quel monstre
 * @param biomes      biomes privilégiés pour l'apparition du PNJ (FIND_NPC)
 * @param optional    quête bonus : ne bloque pas l'amélioration, reste disponible ensuite
 */
public record CityQuest(
        String        id,
        String        displayName,
        String        description,
        Material      icon,
        CityQuestType type,
        int           targetValue,
        CityNPC       targetNpc,
        int           spawnRadius,
        Set<Material> materials,
        EntityType    entity,
        Set<Biome>    biomes,
        boolean       optional
) {

    /* =========================
       FABRIQUES
       ========================= */

    public static CityQuest claim(String id, String name, String desc, int chunks) {
        return new CityQuest(id, name, desc, Material.GRASS_BLOCK, CityQuestType.CLAIM_CHUNKS,
                chunks, null, 0, Set.of(), null, Set.of(), false);
    }

    public static CityQuest build(String id, String name, String desc, int count) {
        return new CityQuest(id, name, desc, Material.BRICKS, CityQuestType.BUILD_BUILDINGS,
                count, null, 0, Set.of(), null, Set.of(), false);
    }

    /** Recruter un PNJ (trouver → ramener → installer). */
    public static CityQuest recruit(String id, String name, String desc, CityNPC npc,
                                    int spawnRadius, Biome... biomes) {
        Set<Biome> set = biomes.length == 0 ? Set.of() : EnumSet.of(biomes[0], biomes);
        return new CityQuest(id, name, desc, Material.COMPASS, CityQuestType.FIND_NPC,
                3, npc, spawnRadius, Set.of(), null, set, false);
    }

    public static CityQuest deliver(String id, String name, String desc, Material icon,
                                    Set<Material> accepted, int amount) {
        return new CityQuest(id, name, desc, icon, CityQuestType.DELIVER_ITEMS,
                amount, null, 0, Set.copyOf(accepted), null, Set.of(), false);
    }

    public static CityQuest stock(String id, String name, String desc, Material icon,
                                  CityNPC npc, Material material, int amount) {
        return new CityQuest(id, name, desc, icon, CityQuestType.STOCK_LEVEL,
                amount, npc, 0, Set.of(material), null, Set.of(), false);
    }

    public static CityQuest npcLevel(String id, String name, String desc, Material icon,
                                     CityNPC npc, int level) {
        return new CityQuest(id, name, desc, icon, CityQuestType.NPC_LEVEL,
                level, npc, 0, Set.of(), null, Set.of(), false);
    }

    /** @param entity null = n'importe quel monstre hostile */
    public static CityQuest kill(String id, String name, String desc, Material icon,
                                 EntityType entity, int amount) {
        return new CityQuest(id, name, desc, icon, CityQuestType.KILL_MOBS,
                amount, null, 0, Set.of(), entity, Set.of(), false);
    }

    /** Copie de la quête en version bonus (optionnelle). */
    public CityQuest asOptional() {
        return new CityQuest(id, displayName, description, icon, type, targetValue, targetNpc,
                spawnRadius, materials, entity, biomes, true);
    }

    /** Premier matériau (STOCK_LEVEL). */
    public Material material() {
        return materials.isEmpty() ? null : materials.iterator().next();
    }
}
